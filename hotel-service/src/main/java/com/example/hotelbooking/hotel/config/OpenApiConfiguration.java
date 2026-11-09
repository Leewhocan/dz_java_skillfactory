package com.example.hotelbooking.hotel.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI hotelServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hotel service API")
                        .version("1.0.0")
                        .description("""
                                Hotels and rooms catalogue (port 8081).

                                * `GET /api/hotels`, `GET /api/rooms`, `GET /api/rooms/recommend` - USER or ADMIN
                                * `POST /api/hotels`, `POST /api/rooms` - ADMIN only
                                * `POST /api/rooms/{id}/confirm-availability`, `POST /api/rooms/{id}/release` -
                                  internal, protected by the `X-Internal-Token` header (booking-service saga)
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
