package com.paulopacifico.gatewayservice.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
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
