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

        verify(chain).filter(any());
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
