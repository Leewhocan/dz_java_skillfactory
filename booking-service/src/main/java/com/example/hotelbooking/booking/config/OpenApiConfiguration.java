package com.example.hotelbooking.booking.config;

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
    public OpenAPI bookingServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking service API")
                        .version("1.0.0")
                        .description("""
                                Users, JWT authentication and bookings (port 8082).

                                * `POST /user/auth`, `POST /user/register` - public
                                * `GET /api/bookings/**` - USER sees own bookings, ADMIN sees all
                                * `POST /api/bookings` - USER, ADMIN (`autoSelect: true` picks the room
                                  with the lowest `timesBooked`)
                                * `DELETE /api/bookings/{id}` - USER (own), ADMIN (any)

                                Tokens: `POST /user/auth` -> `accessToken`, valid for 1 hour.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
