package com.paulopacifico.orderservice.auth;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final JwtProperties jwtProperties;

    public AuthController(JwtService jwtService, AuthProperties authProperties, JwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.authProperties = authProperties;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> getToken(@Valid @RequestBody TokenRequest request) {
        if (!authProperties.username().equals(request.username())
                || !authProperties.password().equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = jwtService.generateToken(request.username());
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(jwtProperties.expirationHours());
        return ResponseEntity.ok(new TokenResponse(token, expiresAt));
    }
}
