package com.example.hotelbooking.hotel.security;

import com.example.hotelbooking.hotel.config.InternalApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String SERVICE_AUTHORITY = "ROLE_SERVICE";
    private static final String DEFAULT_HEADER_NAME = "X-Internal-Token";

    private final String headerName;
    private final byte[] expectedSecret;

    public InternalTokenAuthenticationFilter(InternalApiProperties properties) {
        String secret = properties.token();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.internal.token must be configured: it protects the internal saga endpoints");
        }
        this.expectedSecret = secret.getBytes(StandardCharsets.UTF_8);
        this.headerName = properties.headerName() == null || properties.headerName().isBlank()
                ? DEFAULT_HEADER_NAME
                : properties.headerName();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(headerName);

        if (supplied != null && "POST".equalsIgnoreCase(request.getMethod())
                && MessageDigest.isEqual(expectedSecret, supplied.trim().getBytes(StandardCharsets.UTF_8))) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "booking-service",
                    null,
                    List.of(new SimpleGrantedAuthority(SERVICE_AUTHORITY)));
            authentication.setDetails(request.getRequestURI());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Internal call authenticated for {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
