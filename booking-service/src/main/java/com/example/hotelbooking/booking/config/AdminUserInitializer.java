package com.example.hotelbooking.booking.config;

import com.example.hotelbooking.booking.domain.User;
import com.example.hotelbooking.booking.domain.UserRole;
import com.example.hotelbooking.booking.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminUserInitializer(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${booking.bootstrap-admin.username:admin}") String username,
                                @Value("${booking.bootstrap-admin.password:admin}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(username)) {
            log.info("Bootstrap admin '{}' already exists - skipping creation", username);
            return;
        }

        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(UserRole.ADMIN)
                .build());

        log.info("Bootstrap admin '{}' created", username);
    }
}
