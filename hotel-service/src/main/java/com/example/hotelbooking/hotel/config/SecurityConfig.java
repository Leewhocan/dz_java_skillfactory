package com.example.hotelbooking.hotel.config;

import com.example.hotelbooking.hotel.security.InternalTokenAuthenticationFilter;
import com.example.hotelbooking.hotel.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String USER = "USER";
    private static final String SERVICE = "SERVICE";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalTokenAuthenticationFilter internalTokenAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/rooms/*/confirm-availability",
                                "/api/rooms/*/release").hasRole(SERVICE)
                        .requestMatchers(HttpMethod.GET, "/api/hotels/**", "/api/rooms/**")
                        .hasAnyRole(USER, ADMIN, SERVICE)
                        .requestMatchers(HttpMethod.POST, "/api/hotels", "/api/hotels/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/rooms", "/api/rooms/**").hasRole(ADMIN)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**", "/h2-console/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> writeProblem(response,
                                HttpStatus.UNAUTHORIZED, "Unauthorized", "A valid JWT access token is required"))
                        .accessDeniedHandler((request, response, ex) -> writeProblem(response,
                                HttpStatus.FORBIDDEN, "Forbidden", "Insufficient role for this resource")))
                .addFilterBefore(internalTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                .formatted(title, status.value(), detail));
    }
}
