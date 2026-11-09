package com.example.hotelbooking.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.internal")
public record InternalApiProperties(
        String headerName,
        String token
) {
}
