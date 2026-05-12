# Gateway Merge Hardening Design

**Goal:** Bring PR #2 (`feature/api-gateway`) to merge quality by fixing two critical security vulnerabilities, two high-priority feature gaps, one missing test, and one code-quality issue. No new architecture is introduced — all changes are targeted fixes on the existing gateway feature branch.

**Approach:** Fix-forward on `feature/api-gateway`. Six commits, each scoped to one issue. CI must stay green throughout.

---

## Fix 1 — Bcrypt Password Hashing (CRITICAL)

**Problem:** `AuthController` compares passwords with `authProperties.password().equals(request.password())` — plaintext comparison, password stored as cleartext in env var.

**Change:** Replace `password` field in `AuthProperties` with `passwordHash`. `AuthController` uses `BCryptPasswordEncoder.matches(rawPassword, storedHash)` for comparison. `SecurityConfig` exposes a `PasswordEncoder` bean (cost factor 12).

**Env var change:** `AUTH_PASSWORD` → `AUTH_PASSWORD_HASH` (value is a bcrypt hash, e.g. `$2a$12$...`). Updated in `docker-compose.yml`, `helm/order-service/secret.yaml`, and `helm/order-service/values.yaml`.

**Tests:** `AuthIntegrationTest` and `AuthPropertiesTest` updated to use a pre-computed bcrypt hash constant. No plaintext passwords remain in version control.

**Files touched:**
- `order-service/src/main/java/…/config/AuthProperties.java`
- `order-service/src/main/java/…/config/SecurityConfig.java`
- `order-service/src/main/java/…/controller/AuthController.java`
- `order-service/src/main/resources/application.yml`
- `order-service/src/test/java/…/AuthIntegrationTest.java`
- `docker-compose.yml`
- `helm/order-service/secret.yaml`
- `helm/order-service/values.yaml`

---

## Fix 2 — Remove Hardcoded Demo Credentials (CRITICAL)

**Problem:** `application.yml` defaults `auth.username=demo` and `auth.password=demo` (or `auth.passwordHash` after Fix 1), meaning a service with no env vars configured accepts `demo/demo`.

**Change:** Remove default values from `application.yml`. On startup, `AuthProperties` is validated via `@Validated` + `@NotBlank` — if `AUTH_USERNAME` or `AUTH_PASSWORD_HASH` are absent, the application context fails to start with a clear `BindValidationException` message. This is the same fail-fast pattern already used for DB credentials after the 2026-04-30 hardening.

**Tests:** `application-test.yml` supplies explicit test values (a pre-computed bcrypt hash). No defaults relied upon in any test.

**Files touched:**
- `order-service/src/main/java/…/config/AuthProperties.java`
- `order-service/src/main/resources/application.yml`
- `order-service/src/test/resources/application-test.yml`

---

## Fix 3 — Add Payment and Shipment Routes (HIGH)

**Problem:** `gateway-service/src/main/resources/application.yml` defines routes only for `auth`, `orders`, and `inventory`. Payment (port 8083) and shipment (port 8084) services are unreachable through the gateway.

**Change:** Add two routes to `application.yml`:

| Route ID | Path | Env var | Rate limit (req/s / burst) |
|---|---|---|---|
| `payment-route` | `/api/payments/**` | `PAYMENT_SERVICE_URL` | 10 / 20 |
| `shipment-route` | `/api/shipments/**` | `SHIPMENT_SERVICE_URL` | 10 / 20 |

Both routes require JWT auth (not in the public-path bypass list). Both use the same `ipKeyResolver` and rate profile as the existing `order-route` and `inventory-route`.

**Env vars added:** `PAYMENT_SERVICE_URL` (default `http://localhost:8083`) and `SHIPMENT_SERVICE_URL` (default `http://localhost:8084`) added to `docker-compose.yml` gateway service and `helm/gateway-service/configmap.yaml`.

**Tests:** Two additional integration test cases in `GatewayIntegrationTest` — one for each new route (valid token → WireMock stub returns 200 → gateway returns 200).

**Files touched:**
- `gateway-service/src/main/resources/application.yml`
- `gateway-service/src/test/java/…/GatewayIntegrationTest.java`
- `docker-compose.yml`
- `helm/gateway-service/configmap.yaml`
- `helm/gateway-service/values.yaml`
- `helm/gateway-service/values-dev.yaml`
- `helm/gateway-service/values-prod.yaml`

---

## Fix 4 — CORS Configuration (HIGH)

**Problem:** `order-service` `SecurityConfig` has no CORS configuration. Browser-based clients making cross-origin requests receive a CORS error before the JWT is even checked.

**Change:** Add a `CorsConfigurationSource` bean in `SecurityConfig`. Allowed origins are read from `ALLOWED_ORIGINS` env var (comma-separated list; defaults to `*` for dev convenience but should be set explicitly in prod). Allowed methods: `GET, POST, PUT, DELETE, OPTIONS`. Allowed headers: `Authorization, Content-Type, X-Authenticated-User`. Credentials: `false` (not needed for Bearer token auth). Applied via `.cors(cors -> cors.configurationSource(corsConfigurationSource()))` in the security filter chain.

**Files touched:**
- `order-service/src/main/java/…/config/SecurityConfig.java`
- `order-service/src/main/resources/application.yml`
- `helm/order-service/configmap.yaml`

---

## Fix 5 — JWT Expiration Test (HIGH)

**Problem:** `JwtServiceTest` does not verify that expired tokens are rejected. `JwtService` sets `exp` in the JWT payload, but the behavior on expiry is untested.

**Change:** Add one test to `JwtServiceTest`:

```
whenTokenIsExpired_shouldThrowExpiredJwtException
  - Create JwtProperties with expirationHours = 0 (zero hours → expires at issuedAt)
  - Generate a token
  - Wait 1 second (expired)
  - Call extractUsername(token)
  - Assert ExpiredJwtException is thrown
```

`expirationHours = 0` produces a token with `exp == iat`, which is immediately considered expired by jjwt's parser. No `Thread.sleep` needed.

**Files touched:**
- `order-service/src/test/java/…/JwtServiceTest.java`

---

## Fix 6 — Log Silent JWT Exceptions (MEDIUM)

**Problem:** `JwtAuthFilter.doFilterInternal()` in order-service catches all exceptions in a bare `catch (Exception e) {}` block, silently discarding them. Configuration errors, `NullPointerException`, and unexpected failures are invisible.

**Change:** Replace the empty catch with:
```java
log.warn("JWT validation failed for request to {}: {}", request.getRequestURI(), e.getMessage());
```

Behavior is unchanged (SecurityContext stays empty, Spring Security handles the 401). The log line surfaces unexpected failures without leaking stack traces or token payloads to clients.

**Files touched:**
- `order-service/src/main/java/…/security/JwtAuthFilter.java`

---

## Commit Sequence

| # | Commit message | Fixes |
|---|---|---|
| 1 | `fix: replace plaintext password comparison with bcrypt in AuthController` | Fix 1 |
| 2 | `fix: require AUTH_USERNAME and AUTH_PASSWORD_HASH env vars, remove defaults` | Fix 2 |
| 3 | `feat: add payment and shipment routes to gateway` | Fix 3 |
| 4 | `fix: configure CORS in order-service SecurityConfig` | Fix 4 |
| 5 | `test: verify expired JWT tokens are rejected` | Fix 5 |
| 6 | `fix: log JWT validation failures instead of silently swallowing exceptions` | Fix 6 |

---

## Testing Strategy

- All existing tests must pass throughout. No test deletions.
- Fix 1 and Fix 2: run `order-service` test suite in isolation (`mvn test -pl order-service`).
- Fix 3: run `gateway-service` test suite in isolation (`mvn test -pl gateway-service`).
- Fix 4: run `order-service` test suite; manually verify CORS headers with `curl -H "Origin: http://example.com"`.
- Fix 5: run `order-service` test suite; confirm new test is green.
- Fix 6: run `order-service` test suite.
- Full CI run before requesting merge review.
