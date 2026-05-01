package com.paulopacifico.orderservice.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtProperties(String secret, int expirationHours) {
}
