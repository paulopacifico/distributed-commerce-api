# API Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Spring Cloud Gateway service as the single entry point for all client traffic, enforcing JWT authentication and Redis-backed rate limiting before forwarding to order-service and inventory-service.

**Architecture:** New `gateway-service/` Spring Boot 3.4.3 module (WebFlux/reactive) using Spring Cloud Gateway. A custom `GlobalFilter` validates Bearer tokens using jjwt with the same HS256 shared secret already used by order-service. Rate limiting uses Spring Cloud Gateway's built-in `RequestRateLimiterGatewayFilterFactory` backed by Redis token buckets keyed by client IP.

**Tech Stack:** Spring Boot 3.4.3, Spring Cloud 2024.0.1, Spring Cloud Gateway, Spring Data Redis Reactive, jjwt 0.12.6, Redis 7, Testcontainers, spring-cloud-contract-wiremock, WebTestClient.

---

## File Map

| Status | Path | Purpose |
|--------|------|---------|
| Create | `gateway-service/pom.xml` | Maven module with Spring Cloud Gateway + Redis + jjwt deps |
| Create | `gateway-service/Dockerfile` | Multi-stage build matching existing services |
| Create | `gateway-service/src/main/java/com/paulopacifico/gatewayservice/GatewayServiceApplication.java` | Spring Boot entry point |
| Create | `gateway-service/src/main/java/com/paulopacifico/gatewayservice/config/JwtProperties.java` | `@ConfigurationProperties("jwt")` — secret only |
| Create | `gateway-service/src/main/java/com/paulopacifico/gatewayservice/config/RateLimiterConfig.java` | `ipKeyResolver` bean |
| Create | `gateway-service/src/main/java/com/paulopacifico/gatewayservice/filter/JwtAuthGlobalFilter.java` | JWT GlobalFilter — validate Bearer, forward X-Authenticated-User |
| Create | `gateway-service/src/main/resources/application.yml` | Routes, Redis config, actuator, tracing |
| Create | `gateway-service/src/test/java/com/paulopacifico/gatewayservice/filter/JwtAuthGlobalFilterTest.java` | Unit tests (4 cases, mocked exchange) |
| Create | `gateway-service/src/test/java/com/paulopacifico/gatewayservice/GatewayIntegrationTest.java` | Integration tests (5 cases, WireMock + Testcontainers Redis) |
| Create | `gateway-service/src/test/resources/application-test.yml` | Test-profile routes pointing to WireMock, low burst caps |
| Create | `helm/gateway-service/Chart.yaml` | Chart metadata |
| Create | `helm/gateway-service/values.yaml` | Default values |
| Create | `helm/gateway-service/values-dev.yaml` | Dev overrides |
| Create | `helm/gateway-service/values-prod.yaml` | Prod overrides (HPA enabled) |
| Create | `helm/gateway-service/templates/_helpers.tpl` | Name/label helpers |
| Create | `helm/gateway-service/templates/deployment.yaml` | K8s Deployment |
| Create | `helm/gateway-service/templates/service.yaml` | K8s Service |
| Create | `helm/gateway-service/templates/configmap.yaml` | Non-sensitive env vars |
| Create | `helm/gateway-service/templates/secret.yaml` | JWT_SECRET with required guard |
| Create | `helm/gateway-service/templates/hpa.yaml` | HorizontalPodAutoscaler |
| Modify | `docker-compose.yml` | Add Redis service; move Kafka UI from 8080 → 8090 |
| Modify | `README.md` | Update Kafka UI URL; add gateway run instruction; remove "API Gateway" from Next Steps |
| Modify | `.github/workflows/ci-cd.yml` | Add gateway build/test/docker/helm steps |

---

### Task 1: Worktree + Maven module skeleton

**Files:**
- Create: `gateway-service/pom.xml`
- Create: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/GatewayServiceApplication.java`

- [ ] **Step 1: Create the feature branch worktree**

```bash
cd C:/Users/paulo/distributed-commerce-api
git worktree add .worktrees/feature-api-gateway -b feature/api-gateway
```

Expected: `.worktrees/feature-api-gateway/` created on branch `feature/api-gateway`.

- [ ] **Step 2: Create `gateway-service/pom.xml`**

All subsequent file creation commands run inside `.worktrees/feature-api-gateway/`.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>

    <groupId>com.paulopacifico</groupId>
    <artifactId>gateway-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>gateway-service</name>
    <description>API Gateway for the distributed commerce platform</description>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2024.0.1</spring-cloud.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-contract-wiremock</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <environmentVariables>
                        <DOCKER_HOST>unix:///var/run/docker.sock</DOCKER_HOST>
                        <TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>/var/run/docker.sock</TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>
                        <TESTCONTAINERS_DOCKER_CLIENT_STRATEGY>org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy</TESTCONTAINERS_DOCKER_CLIENT_STRATEGY>
                        <TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>
                        <TESTCONTAINERS_CHECKS_DISABLE>true</TESTCONTAINERS_CHECKS_DISABLE>
                    </environmentVariables>
                    <systemPropertyVariables>
                        <testcontainers.ryuk.disabled>true</testcontainers.ryuk.disabled>
                        <api.version>1.40</api.version>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>skip-integration-tests</id>
            <activation>
                <property>
                    <name>skipIntegrationTests</name>
                </property>
            </activation>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <configuration>
                            <excludes>
                                <exclude>**/*IntegrationTest.java</exclude>
                            </excludes>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 3: Create `GatewayServiceApplication.java`**

Path: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/GatewayServiceApplication.java`

```java
package com.paulopacifico.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add gateway-service/
git commit -m "chore: bootstrap gateway-service Maven module"
```

---

### Task 2: JwtProperties + JwtAuthGlobalFilter (TDD)

**Files:**
- Create: `gateway-service/src/test/java/com/paulopacifico/gatewayservice/filter/JwtAuthGlobalFilterTest.java`
- Create: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/config/JwtProperties.java`
- Create: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/filter/JwtAuthGlobalFilter.java`

- [ ] **Step 1: Write the failing unit test**

Path: `gateway-service/src/test/java/com/paulopacifico/gatewayservice/filter/JwtAuthGlobalFilterTest.java`

```java
package com.paulopacifico.gatewayservice.filter;

import com.paulopacifico.gatewayservice.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthGlobalFilterTest {

    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-long-enough-32b";

    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter(new JwtProperties(TEST_SECRET));
    }

    @Test
    void shouldSkipAuthForPublicAuthPath() {
        var request = MockServerHttpRequest.post("/api/auth/token").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void shouldReturn401WhenNoAuthorizationHeader() {
        var request = MockServerHttpRequest.get("/api/orders").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void shouldReturn401ForInvalidToken() {
        var request = MockServerHttpRequest.get("/api/orders")
                .header("Authorization", "Bearer invalid.jwt.token")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void shouldForwardWithUserHeaderForValidToken() {
        String token = buildToken("alice");
        var request = MockServerHttpRequest.get("/api/orders")
                .header("Authorization", "Bearer " + token)
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        var captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-Authenticated-User"))
                .isEqualTo("alice");
    }

    private String buildToken(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}
```

- [ ] **Step 2: Run test — expect compile failure (classes don't exist yet)**

```bash
cd gateway-service && mvn -B test -pl . -Dtest=JwtAuthGlobalFilterTest -DfailIfNoTests=false 2>&1 | tail -20
```

Expected: `COMPILATION ERROR` — `JwtAuthGlobalFilter` and `JwtProperties` not found.

Note: if `mvn` is not in PATH, push the branch and let CI fail at this step. Proceed to implementation regardless.

- [ ] **Step 3: Create `JwtProperties.java`**

Path: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/config/JwtProperties.java`

```java
package com.paulopacifico.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtProperties(String secret) {}
```

- [ ] **Step 4: Create `JwtAuthGlobalFilter.java`**

Path: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/filter/JwtAuthGlobalFilter.java`

```java
package com.paulopacifico.gatewayservice.filter;

import com.paulopacifico.gatewayservice.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_HEADER = "X-Authenticated-User";
    private static final String PUBLIC_PATH_PREFIX = "/api/auth/";

    private final JwtProperties jwtProperties;

    public JwtAuthGlobalFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(PUBLIC_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            String username = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();

            ServerWebExchange mutated = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header(USER_HEADER, username)
                            .build())
                    .build();
            return chain.filter(mutated);
        } catch (JwtException | IllegalArgumentException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 5: Run the unit tests — expect all 4 to pass**

```bash
cd gateway-service && mvn -B test -Dtest=JwtAuthGlobalFilterTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add gateway-service/src/
git commit -m "feat: add JwtProperties and JwtAuthGlobalFilter with HS256 validation"
```

---

### Task 3: Routes + RateLimiterConfig + Integration test (TDD)

**Files:**
- Create: `gateway-service/src/test/java/com/paulopacifico/gatewayservice/GatewayIntegrationTest.java`
- Create: `gateway-service/src/test/resources/application-test.yml`
- Create: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/config/RateLimiterConfig.java`
- Create: `gateway-service/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing integration test**

Path: `gateway-service/src/test/java/com/paulopacifico/gatewayservice/GatewayIntegrationTest.java`

```java
package com.paulopacifico.gatewayservice;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@Testcontainers
class GatewayIntegrationTest {

    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-long-enough-32b";

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    private String validToken;

    @BeforeEach
    void setUp() {
        redisConnectionFactory.getReactiveConnection()
                .serverCommands().flushDb().block();

        WireMock.reset();
        stubFor(get(urlEqualTo("/api/orders")).willReturn(okJson("[]")));
        stubFor(post(urlEqualTo("/api/auth/token"))
                .willReturn(okJson("{\"token\":\"abc\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}")));

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        validToken = Jwts.builder()
                .subject("testuser")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    @Test
    void shouldReturn401WhenNoToken() {
        webTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturn401ForInvalidToken() {
        webTestClient.get().uri("/api/orders")
                .header("Authorization", "Bearer not.a.valid.token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldForwardRequestWithValidToken() {
        webTestClient.get().uri("/api/orders")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldForwardPublicAuthRouteWithoutToken() {
        webTestClient.post().uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"demo\",\"password\":\"demo\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldReturn429WhenBurstCapacityExceeded() {
        // application-test.yml sets burstCapacity: 2 for order-route
        // First 2 requests consume the burst; 3rd is rejected
        for (int i = 0; i < 2; i++) {
            webTestClient.get().uri("/api/orders")
                    .header("Authorization", "Bearer " + validToken)
                    .exchange()
                    .expectStatus().isOk();
        }
        webTestClient.get().uri("/api/orders")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
```

- [ ] **Step 2: Create `application-test.yml`**

Path: `gateway-service/src/test/resources/application-test.yml`

```yaml
jwt:
  secret: "test-secret-key-for-unit-tests-long-enough-32b"

spring:
  cloud:
    gateway:
      routes:
        - id: order-route
          uri: http://localhost:${wiremock.server.port}
          predicates:
            - Path=/api/orders/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 2
                key-resolver: "#{@ipKeyResolver}"
        - id: auth-route
          uri: http://localhost:${wiremock.server.port}
          predicates:
            - Path=/api/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                key-resolver: "#{@ipKeyResolver}"
```

- [ ] **Step 3: Create `RateLimiterConfig.java`**

Path: `gateway-service/src/main/java/com/paulopacifico/gatewayservice/config/RateLimiterConfig.java`

```java
package com.paulopacifico.gatewayservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var address = exchange.getRequest().getRemoteAddress();
            if (address == null) {
                return Mono.error(new IllegalStateException("Cannot resolve client IP address"));
            }
            return Mono.just(address.getAddress().getHostAddress());
        };
    }
}
```

- [ ] **Step 4: Create `application.yml`**

Path: `gateway-service/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: gateway-service
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cloud:
    gateway:
      routes:
        - id: auth-route
          uri: ${ORDER_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 20
                redis-rate-limiter.burstCapacity: 40
                key-resolver: "#{@ipKeyResolver}"
        - id: order-route
          uri: ${ORDER_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/orders/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"
        - id: inventory-route
          uri: ${INVENTORY_SERVICE_URL:http://localhost:8082}
          predicates:
            - Path=/api/inventory/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"

server:
  port: ${SERVER_PORT:8080}

jwt:
  secret: ${JWT_SECRET}

management:
  endpoint:
    health:
      probes:
        enabled: true
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [traceId=%X{traceId:-}, spanId=%X{spanId:-}] %-5level [%t] %logger{50} : %m%n"
  level:
    root: INFO
    com.paulopacifico.gatewayservice: INFO
```

- [ ] **Step 5: Run all tests — expect all 9 to pass**

```bash
cd gateway-service && mvn -B clean verify -Dspring.profiles.active=test
```

Expected: `Tests run: 9, Failures: 0, Errors: 0` (4 unit + 5 integration).

- [ ] **Step 6: Commit**

```bash
git add gateway-service/src/main/ gateway-service/src/test/
git commit -m "feat: add RateLimiterConfig, routes, and integration tests"
```

---

### Task 4: Dockerfile

**Files:**
- Create: `gateway-service/Dockerfile`

- [ ] **Step 1: Create the Dockerfile**

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/gateway-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Commit**

```bash
git add gateway-service/Dockerfile
git commit -m "chore: add gateway-service Dockerfile"
```

---

### Task 5: Helm chart

**Files:** All under `helm/gateway-service/`

- [ ] **Step 1: Create `helm/gateway-service/Chart.yaml`**

```yaml
apiVersion: v2
name: gateway-service
description: API Gateway for the distributed commerce platform — JWT auth, rate limiting, and routing
type: application
version: 0.1.0
appVersion: "0.0.1-SNAPSHOT"
```

- [ ] **Step 2: Create `helm/gateway-service/values.yaml`**

```yaml
image:
  repository: gateway-service
  tag: latest
  pullPolicy: IfNotPresent

replicaCount: 1

service:
  type: ClusterIP
  port: 8080

resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi

config:
  serverPort: "8080"
  orderServiceUrl: "http://order-service:8081"
  inventoryServiceUrl: "http://inventory-service:8082"
  redisHost: "redis"
  redisPort: "6379"
  zipkinEndpoint: "http://zipkin:9411/api/v2/spans"
  tracingSamplingProbability: "1.0"

# Sensitive values — never commit real credentials.
# Supply at deploy time via: --set secret.jwtSecret=...
secret:
  jwtSecret: ""

hpa:
  enabled: false
  minReplicas: 1
  maxReplicas: 3
  targetCPUUtilizationPercentage: 70

probes:
  liveness:
    path: /actuator/health/liveness
    initialDelaySeconds: 30
    periodSeconds: 15
    failureThreshold: 3
  readiness:
    path: /actuator/health/readiness
    initialDelaySeconds: 15
    periodSeconds: 10
    failureThreshold: 3
```

- [ ] **Step 3: Create `helm/gateway-service/values-dev.yaml`**

```yaml
replicaCount: 1

resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 500m
    memory: 256Mi

config:
  orderServiceUrl: "http://order-service:8081"
  inventoryServiceUrl: "http://inventory-service:8082"
  redisHost: "redis"
  zipkinEndpoint: "http://zipkin:9411/api/v2/spans"

hpa:
  enabled: false
```

- [ ] **Step 4: Create `helm/gateway-service/values-prod.yaml`**

```yaml
replicaCount: 2

image:
  pullPolicy: Always

resources:
  requests:
    cpu: 250m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi

config:
  orderServiceUrl: "${ORDER_SERVICE_URL}"
  inventoryServiceUrl: "${INVENTORY_SERVICE_URL}"
  redisHost: "${REDIS_HOST}"
  zipkinEndpoint: "${ZIPKIN_ENDPOINT}"
  tracingSamplingProbability: "0.1"

hpa:
  enabled: true
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70
```

- [ ] **Step 5: Create `helm/gateway-service/templates/_helpers.tpl`**

```
{{/*
Fully qualified app name: <release>-<chart>, truncated to 63 chars.
*/}}
{{- define "gateway-service.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "gateway-service.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels used by Deployment and Service to match pods.
*/}}
{{- define "gateway-service.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
```

- [ ] **Step 6: Create `helm/gateway-service/templates/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "gateway-service.fullname" . }}
  labels:
    {{- include "gateway-service.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "gateway-service.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "gateway-service.selectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: {{ .Values.service.port }}
              protocol: TCP
          envFrom:
            - configMapRef:
                name: {{ include "gateway-service.fullname" . }}
            - secretRef:
                name: {{ include "gateway-service.fullname" . }}
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness.path }}
              port: {{ .Values.service.port }}
            initialDelaySeconds: {{ .Values.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
            failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness.path }}
              port: {{ .Values.service.port }}
            initialDelaySeconds: {{ .Values.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
            failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

- [ ] **Step 7: Create `helm/gateway-service/templates/service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "gateway-service.fullname" . }}
  labels:
    {{- include "gateway-service.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  selector:
    {{- include "gateway-service.selectorLabels" . | nindent 4 }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: {{ .Values.service.port }}
      protocol: TCP
```

- [ ] **Step 8: Create `helm/gateway-service/templates/configmap.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "gateway-service.fullname" . }}
  labels:
    {{- include "gateway-service.labels" . | nindent 4 }}
data:
  SERVER_PORT: {{ .Values.config.serverPort | quote }}
  ORDER_SERVICE_URL: {{ .Values.config.orderServiceUrl | quote }}
  INVENTORY_SERVICE_URL: {{ .Values.config.inventoryServiceUrl | quote }}
  REDIS_HOST: {{ .Values.config.redisHost | quote }}
  REDIS_PORT: {{ .Values.config.redisPort | quote }}
  ZIPKIN_ENDPOINT: {{ .Values.config.zipkinEndpoint | quote }}
  TRACING_SAMPLING_PROBABILITY: {{ .Values.config.tracingSamplingProbability | quote }}
```

- [ ] **Step 9: Create `helm/gateway-service/templates/secret.yaml`**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "gateway-service.fullname" . }}
  labels:
    {{- include "gateway-service.labels" . | nindent 4 }}
type: Opaque
data:
  JWT_SECRET: {{ required "secret.jwtSecret must be set at install time" .Values.secret.jwtSecret | b64enc | quote }}
```

- [ ] **Step 10: Create `helm/gateway-service/templates/hpa.yaml`**

```yaml
{{- if .Values.hpa.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "gateway-service.fullname" . }}
  labels:
    {{- include "gateway-service.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "gateway-service.fullname" . }}
  minReplicas: {{ .Values.hpa.minReplicas }}
  maxReplicas: {{ .Values.hpa.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.hpa.targetCPUUtilizationPercentage }}
{{- if .Values.hpa.enabled }}
```

Wait — the closing `{{- end }}` above is wrong. The correct closing is just `{{- end }}` once. Here is the corrected file:

```yaml
{{- if .Values.hpa.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "gateway-service.fullname" . }}
  labels:
    {{- include "gateway-service.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "gateway-service.fullname" . }}
  minReplicas: {{ .Values.hpa.minReplicas }}
  maxReplicas: {{ .Values.hpa.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.hpa.targetCPUUtilizationPercentage }}
{{- end }}
```

- [ ] **Step 11: Verify Helm charts render without errors**

```bash
helm lint helm/gateway-service -f helm/gateway-service/values-dev.yaml
helm lint helm/gateway-service -f helm/gateway-service/values-prod.yaml
helm template gateway-service helm/gateway-service -f helm/gateway-service/values-dev.yaml --set secret.jwtSecret=ci > /dev/null
helm template gateway-service helm/gateway-service -f helm/gateway-service/values-prod.yaml --set secret.jwtSecret=ci > /dev/null
```

Expected: no errors from any of the four commands.

- [ ] **Step 12: Commit**

```bash
git add helm/gateway-service/
git commit -m "chore: add gateway-service Helm chart"
```

---

### Task 6: docker-compose + README

**Files:**
- Modify: `docker-compose.yml`
- Modify: `README.md`

- [ ] **Step 1: Update `docker-compose.yml`**

Make two changes:

**Change 1** — move Kafka UI from port 8080 to 8090. Find:
```yaml
  kafka-ui:
    ...
    ports:
      - "8080:8080"
```
Replace with:
```yaml
  kafka-ui:
    ...
    ports:
      - "8090:8080"
```

**Change 2** — add the Redis service. Insert this block after the `zipkin` service and before the `volumes:` section:

```yaml
  redis:
    image: redis:7-alpine
    container_name: dc-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 5s
```

- [ ] **Step 2: Update `README.md`**

Make three targeted changes:

**Change 1** — in the container table under "How to Run Locally", update the Kafka UI row:

Find:
```
| Kafka UI | `http://localhost:8080` | Browse topics, consumer groups, and message payloads |
```
Replace with:
```
| Kafka UI | `http://localhost:8090` | Browse topics, consumer groups, and message payloads |
| Redis | `localhost:6379` | Rate-limiter token bucket state (used by gateway) |
```

**Change 2** — add a Terminal 5 for starting the gateway in the "Start the services" block:

Find:
```bash
# Terminal 4
cd shipment-service && mvn spring-boot:run
```
Replace with:
```bash
# Terminal 4
cd shipment-service && mvn spring-boot:run

# Terminal 5 — API Gateway (single entry point for all client requests)
# Set JWT_SECRET to the same value used by order-service
cd gateway-service && JWT_SECRET=your-secret-here mvn spring-boot:run
```

**Change 3** — remove "API Gateway" from the "Next Steps" section:

Find:
```markdown
## Next Steps

- **API Gateway** — single entry point with rate limiting and routing across services
```
Replace with:
```markdown
## Next Steps

- **OpenAPI documentation** — springdoc-openapi on order-service for interactive JWT-protected API exploration
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml README.md
git commit -m "chore: add Redis to docker-compose, move Kafka UI to port 8090"
```

---

### Task 7: CI/CD pipeline

**Files:**
- Modify: `.github/workflows/ci-cd.yml`

- [ ] **Step 1: Add Redis image pull to the pre-pull step**

Find:
```yaml
      - name: Pull Testcontainers images
        run: |
          docker pull postgres:16-alpine
          docker pull apache/kafka-native:3.8.0
```
Replace with:
```yaml
      - name: Pull Testcontainers images
        run: |
          docker pull postgres:16-alpine
          docker pull apache/kafka-native:3.8.0
          docker pull redis:7-alpine
```

- [ ] **Step 2: Add gateway-service build and test step**

Find:
```yaml
      - name: Build and test shipment-service
        working-directory: shipment-service
        run: mvn -B clean verify -Dspring.profiles.active=test
```
Replace with:
```yaml
      - name: Build and test shipment-service
        working-directory: shipment-service
        run: mvn -B clean verify -Dspring.profiles.active=test

      - name: Build and test gateway-service
        working-directory: gateway-service
        run: mvn -B clean verify -Dspring.profiles.active=test
```

- [ ] **Step 3: Add gateway-service surefire reports to the upload step**

Find:
```yaml
          path: |
            order-service/target/surefire-reports/
            inventory-service/target/surefire-reports/
            payment-service/target/surefire-reports/
            shipment-service/target/surefire-reports/
```
Replace with:
```yaml
          path: |
            order-service/target/surefire-reports/
            inventory-service/target/surefire-reports/
            payment-service/target/surefire-reports/
            shipment-service/target/surefire-reports/
            gateway-service/target/surefire-reports/
```

- [ ] **Step 4: Add gateway-service Docker image build step**

Find:
```yaml
      - name: Build Docker image for shipment-service
        run: docker build -t shipment-service:ci ./shipment-service
```
Replace with:
```yaml
      - name: Build Docker image for shipment-service
        run: docker build -t shipment-service:ci ./shipment-service

      - name: Build Docker image for gateway-service
        run: docker build -t gateway-service:ci ./gateway-service
```

- [ ] **Step 5: Add gateway-service Helm lint**

Find:
```yaml
          helm lint helm/shipment-service  -f helm/shipment-service/values-dev.yaml
          helm lint helm/shipment-service  -f helm/shipment-service/values-prod.yaml
```
Replace with:
```yaml
          helm lint helm/shipment-service  -f helm/shipment-service/values-dev.yaml
          helm lint helm/shipment-service  -f helm/shipment-service/values-prod.yaml
          helm lint helm/gateway-service   -f helm/gateway-service/values-dev.yaml
          helm lint helm/gateway-service   -f helm/gateway-service/values-prod.yaml
```

- [ ] **Step 6: Add gateway-service Helm template dry-run**

Find:
```yaml
          helm template shipment-service  helm/shipment-service  -f helm/shipment-service/values-dev.yaml  --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template shipment-service  helm/shipment-service  -f helm/shipment-service/values-prod.yaml --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
```
Replace with:
```yaml
          helm template shipment-service  helm/shipment-service  -f helm/shipment-service/values-dev.yaml  --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template shipment-service  helm/shipment-service  -f helm/shipment-service/values-prod.yaml --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template gateway-service   helm/gateway-service   -f helm/gateway-service/values-dev.yaml   --set secret.jwtSecret=ci > /dev/null
          helm template gateway-service   helm/gateway-service   -f helm/gateway-service/values-prod.yaml  --set secret.jwtSecret=ci > /dev/null
```

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/ci-cd.yml
git commit -m "ci: add gateway-service build, test, Docker, and Helm steps"
```

---

## Self-Review Checklist

### Spec coverage

| Spec requirement | Task |
|---|---|
| New `gateway-service` Spring Boot module | Task 1 |
| `JwtProperties` — secret only | Task 2 |
| `JwtAuthGlobalFilter` — skip `/api/auth/`, validate Bearer, add `X-Authenticated-User` | Task 2 |
| Redis-backed rate limiting, `ipKeyResolver` | Task 3 |
| Routes: auth-route, order-route, inventory-route | Task 3 |
| `application.yml` with env-var-driven URIs and Redis config | Task 3 |
| Unit tests: 4 cases | Task 2 |
| Integration tests: 5 cases (WireMock + Testcontainers Redis) | Task 3 |
| `Dockerfile` multi-stage | Task 4 |
| Helm chart (all 10 files) | Task 5 |
| `docker-compose.yml`: add Redis, move Kafka UI to 8090 | Task 6 |
| `README.md`: update Kafka UI URL, add gateway run instruction | Task 6 |
| CI: gateway build/test/Docker/Helm | Task 7 |

All spec requirements covered. No gaps.

### Type consistency

- `JwtProperties(String secret)` — used identically in `JwtAuthGlobalFilter`, `RateLimiterConfig` (via `@EnableConfigurationProperties`), and `JwtAuthGlobalFilterTest`
- `TEST_SECRET` constant `"test-secret-key-for-unit-tests-long-enough-32b"` — identical in `JwtAuthGlobalFilterTest` and `GatewayIntegrationTest`
- `application-test.yml` `jwt.secret` matches `TEST_SECRET` in both test classes
- `hpa.yaml` closing `{{- end }}` appears exactly once — corrected in Step 10
