package com.paulopacifico.orderservice.auth;

import java.time.OffsetDateTime;

public record TokenResponse(String token, OffsetDateTime expiresAt) {
}
