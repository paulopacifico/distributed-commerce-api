package com.paulopacifico.orderservice.auth;

import com.paulopacifico.orderservice.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        var response = testRestTemplate.getForEntity(
                "http://localhost:" + port + "/api/orders",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401ForInvalidCredentials() {
        var response = testRestTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/token",
                Map.of("username", "demo", "password", "wrong"),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldIssueTokenForValidCredentials() {
        var response = testRestTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/token",
                Map.of("username", "demo", "password", "demo"),
                TokenResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().expiresAt()).isNotNull();
    }

    @Test
    void shouldAllowAuthenticatedRequestToGetOrders() {
        var headers = authHeaders(port, testRestTemplate);
        var response = testRestTemplate.exchange(
                "http://localhost:" + port + "/api/orders",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAllowUnauthenticatedAccessToActuatorHealth() {
        var response = testRestTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
