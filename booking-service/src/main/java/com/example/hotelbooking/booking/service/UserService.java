package com.example.hotelbooking.booking.service;

import com.example.hotelbooking.booking.domain.User;
import com.example.hotelbooking.booking.domain.UserRole;
import com.example.hotelbooking.booking.dto.AdminUserRequest;
import com.example.hotelbooking.booking.dto.AuthRequest;
import com.example.hotelbooking.booking.dto.AuthResponse;
import com.example.hotelbooking.booking.dto.RegisterRequest;
import com.example.hotelbooking.booking.dto.UserResponse;
import com.example.hotelbooking.booking.dto.UserUpdateRequest;
import com.example.hotelbooking.booking.exception.BadRequestException;
import com.example.hotelbooking.booking.exception.ConflictException;
import com.example.hotelbooking.booking.exception.ResourceNotFoundException;
import com.example.hotelbooking.booking.mapper.UserMapper;
import com.example.hotelbooking.booking.repository.BookingRepository;
import com.example.hotelbooking.booking.repository.UserRepository;
import com.example.hotelbooking.booking.security.JwtService;
import com.example.hotelbooking.booking.security.UserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MIN_USERNAME_LENGTH = 3;

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String password = requirePassword(request.password());

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }

        User user = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(UserRole.USER)
                .build());

        log.info("Registered new user '{}' with role {}", username, user.getRole());
        return issueToken(new UserPrincipal(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isEmpty()) {
            throw new BadRequestException("username and password must not be empty");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        log.info("User '{}' authenticated successfully", principal.getUsername());
        return issueToken(principal);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userMapper.toResponse(findUser(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userMapper.toResponse(userRepository.findAll());
    }

    @Transactional
    public UserResponse createUser(AdminUserRequest request) {
        String username = normalizeUsername(request.username());
        String password = requirePassword(request.password());

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }

        User user = userMapper.toEntity(request);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(request.role() == null ? UserRole.USER : request.role());

        return userMapper.toResponse(userRepository.save(user));
    }
    @Transactional
    public UserResponse updateUser(UserUpdateRequest request) {
        if (request.id() == null) {
            throw new BadRequestException("id must not be null");
        }

        User user = findUser(request.id());

        if (request.username() != null && !request.username().isBlank()) {
            String username = normalizeUsername(request.username());
            if (!username.equals(user.getUsername())) {
                if (userRepository.existsByUsername(username)) {
                    throw new ConflictException("Username '" + username + "' is already taken");
                }
                user.setUsername(username);
            }
        }

        if (request.password() != null && !request.password().isEmpty()) {
            user.setPassword(passwordEncoder.encode(requirePassword(request.password())));
        }

        if (request.role() != null) {
            user.setRole(request.role());
        }

        log.info("User {} updated", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (id == null) {
            throw new BadRequestException("id must not be null");
        }

        User user = findUser(id);

        if (user.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new ConflictException("The last ADMIN user cannot be deleted");
        }
        if (bookingRepository.existsByUserId(id)) {
            throw new ConflictException("User " + id + " owns bookings and cannot be deleted");
        }

        userRepository.delete(user);
        log.info("User {} deleted", id);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
    }

    private AuthResponse issueToken(UserPrincipal principal) {
        return new AuthResponse(
                jwtService.generateToken(principal),
                "Bearer",
                jwtService.expirationSeconds(),
                principal.getUsername(),
                principal.getRole().name());
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("username must not be blank");
        }
        String trimmed = username.trim();
        if (trimmed.length() < MIN_USERNAME_LENGTH) {
            throw new BadRequestException("username must be at least " + MIN_USERNAME_LENGTH + " characters long");
        }
        return trimmed;
    }

    private static String requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        return password;
    }
}
