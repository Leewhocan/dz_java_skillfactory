package com.example.hotelbooking.booking.client;

import com.example.hotelbooking.booking.config.InternalApiProperties;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class HotelClientConfiguration {

    private static final String DEFAULT_INTERNAL_HEADER = "X-Internal-Token";

    @Bean
    public Retryer hotelClientRetryer() {
        return new IdempotentRetryer(100, 500, 2);
    }

    @Bean
    public Logger.Level hotelClientLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public RequestInterceptor hotelClientHeaderInterceptor(InternalApiProperties properties) {
        String headerName = properties.headerName() == null || properties.headerName().isBlank()
                ? DEFAULT_INTERNAL_HEADER
                : properties.headerName();
        String internalToken = properties.token();
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "security.internal.token must be configured: it protects the internal hotel-service endpoints");
        }

        return template -> {
            template.header(headerName, internalToken);
            String authorization = currentAuthorization();
            if (authorization != null) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }

    private static String currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        return authorization == null || authorization.isBlank() ? null : authorization;
    }
}
