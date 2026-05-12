# API Gateway Design

**Goal:** Add a Spring Cloud Gateway service as the single entry point for all client traffic, enforcing JWT authentication and Redis-backed rate limiting before forwarding requests to order-service and inventory-service.

**Architecture:** New `gateway-service` Spring Boot module (WebFlux/reactive) sits in front of order-service and inventory-service. A custom `GlobalFilter` validates Bearer tokens using the same HS256 shared secret already used by order-service, providing a defense-in-depth model where both the gateway and order-service independently verify JWTs. Rate limiting uses Spring Cloud Gateway's built-in `RequestRateLimiterGatewayFilterFactory` backed by Redis.

**Tech stack:** Spring Boot 3.4.3, Spring Cloud Gateway 2024.0.x, Spring Data Redis Reactive, jjwt 0.12.6, Redis 7, Testcontainers Redis, WireMock (spring-cloud-contract-wiremock), WebTestClient.

---

## Routes

Three routes defined in `application.yml` YAML DSL:

| Route ID | Path | Upstream | Auth required | Rate limit (req/s / burst) |
|---|---|---|---|---|
| `auth-route` | `/api/auth/**` | order-service | No | 20 / 40 |
| `order-route` | `/api/orders/**` | order-service | Yes | 10 / 20 |
| `inventory-route` | `/api/inventory/**` | inventory-service | Yes | 10 / 20 |

Upstream URIs come from `ORDER_SERVICE_URL` and `INVENTORY_SERVICE_URL` env vars (default: `http://localhost:8081` and `http://localhost:8082`).

---

## JWT Filter

`JwtAuthGlobalFilter` implements `GlobalFilter` and `Ordered` (runs before the routing filter).

**Logic per request:**
1. Path starts with `/api/auth/` → skip, call `chain.filter(exchange)` immediately
2. No `Authorization` header, or header does not start with `Bearer ` → return 401
3. Parse and verify the JWT with jjwt (`Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`); if expired or tampered → return 401
4. Extract subject (username); mutate the forwarded request to add `X-Authenticated-User: <username>` header; call `chain.filter(mutatedExchange)`

`JwtProperties` is a `@ConfigurationProperties("jwt")` record with a single `secret` field (the gateway validates tokens only — it never issues them, so `expirationHours` is not needed). Registered via `@EnableConfigurationProperties` on a `@Configuration` class. `JWT_SECRET` env var is shared with order-service — both services validate using the same HS256 key.

No Spring Security dependency on the gateway. Spring Security remains in order-service only (defense-in-depth layer).

---

## Rate Limiting

Algorithm: Redis token bucket via `RequestRateLimiterGatewayFilterFactory`.

**Key resolver:** `ipKeyResolver` bean in `RateLimiterConfig` resolves bucket key from `exchange.getRequest().getRemoteAddress()`. If the address is unresolvable, the request is rejected with 429 rather than falling back to a shared bucket.

**Rate profiles:**

| Route | replenishRate | burstCapacity | Rationale |
|---|---|---|---|
| `/api/auth/**` | 20 | 40 | Higher limit — throttling auth blocks all other requests |
| `/api/orders/**` | 10 | 20 | Standard API limit |
| `/api/inventory/**` | 10 | 20 | Standard API limit |

429 responses are returned automatically by the framework when the bucket is exhausted.

---

## Project Structure

```
gateway-service/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/paulopacifico/gatewayservice/
    │   │   ├── GatewayServiceApplication.java
    │   │   ├── config/
    │   │   │   ├── JwtProperties.java          -- @ConfigurationProperties("jwt")
    │   │   │   └── RateLimiterConfig.java       -- ipKeyResolver bean
    │   │   └── filter/
    │   │       └── JwtAuthGlobalFilter.java     -- GlobalFilter + Ordered
    │   └── resources/
    │       └── application.yml
    └── test/
        ├── java/com/paulopacifico/gatewayservice/
        │   ├── filter/
        │   │   └── JwtAuthGlobalFilterTest.java  -- unit tests, mocked exchange
        │   └── GatewayIntegrationTest.java        -- WebTestClient + WireMock + Testcontainers Redis
        └── resources/
            └── application-test.yml
```

---

## Dependencies (`pom.xml`)

- `spring-cloud-starter-gateway` (via Spring Cloud 2024.0.x BOM)
- `spring-boot-starter-data-redis-reactive`
- `io.jsonwebtoken:jjwt-api:0.12.6`
- `io.jsonwebtoken:jjwt-impl:0.12.6` (runtime)
- `io.jsonwebtoken:jjwt-jackson:0.12.6` (runtime)
- `spring-boot-starter-actuator`
- `spring-boot-starter-test`
- `spring-cloud-contract-wiremock` (test)
- `org.testcontainers:testcontainers` (test)

---

## Infrastructure Changes

### docker-compose.yml

- Kafka UI port: `8080:8080` → `8090:8080` (frees port 8080 for the gateway)
- Add `redis` service: `redis:7-alpine`, port `6379:6379`, no volume (in-memory only)
- Add `gateway-service` service: port `8080:8080`, depends on `redis`, `order-service`, `inventory-service`

### Helm — `helm/gateway-service/`

Same chart structure as the four existing services:

| Template | Purpose |
|---|---|
| `_helpers.tpl` | Name/label helpers |
| `deployment.yaml` | Deployment with liveness/readiness probes on `/actuator/health` |
| `service.yaml` | ClusterIP service |
| `configmap.yaml` | `SERVER_PORT`, `ORDER_SERVICE_URL`, `INVENTORY_SERVICE_URL`, `REDIS_HOST`, `REDIS_PORT`, `ZIPKIN_ENDPOINT` |
| `secret.yaml` | `JWT_SECRET` with `required` guard (same pattern as order-service) |
| `hpa.yaml` | HPA |
| `values.yaml` | Default values + `dev`/`prod` overrides |

### CI — `.github/workflows/`

Three additions matching the existing per-service pattern:
1. Maven build + test step for `gateway-service`
2. Docker image build step for `gateway-service`
3. Helm lint + template dry-run for gateway-service (dev + prod value combinations)

---

## Testing

### Unit — `JwtAuthGlobalFilterTest`

Mocked `ServerWebExchange` and `GatewayFilterChain`. Four cases:

| Scenario | Expected |
|---|---|
| Path `/api/auth/token`, no token | Chain called (public path skipped) |
| Path `/api/orders`, no `Authorization` header | 401 |
| Path `/api/orders`, `Authorization: Bearer <tampered>` | 401 |
| Path `/api/orders`, valid signed JWT | Chain called, `X-Authenticated-User` header present on forwarded request |

### Integration — `GatewayIntegrationTest`

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` + WireMock stubs for downstream services + Testcontainers Redis. Five cases:

| Scenario | Expected |
|---|---|
| No token on protected route | 401 (never reaches stub) |
| Invalid token on protected route | 401 (never reaches stub) |
| Valid token, stub returns 200 | Gateway returns 200 |
| No token on `/api/auth/token` (public) | Forwarded to stub, stub returns 200 |
| 21 requests with valid token, burst capacity 20 | First 20 pass, 21st returns 429 |
