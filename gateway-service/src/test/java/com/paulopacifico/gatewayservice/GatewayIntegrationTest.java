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
