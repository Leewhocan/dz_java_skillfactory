package com.example.hotelbooking.hotel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.internal")
public record InternalApiProperties(
        String headerName,
        String token
) {
}
