package com.paulopacifico.orderservice.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String username, String password) {
}
