package com.example.hotelbooking.booking.controller;

import com.example.hotelbooking.booking.dto.AdminUserRequest;
import com.example.hotelbooking.booking.dto.AuthRequest;
import com.example.hotelbooking.booking.dto.AuthResponse;
import com.example.hotelbooking.booking.dto.RegisterRequest;
import com.example.hotelbooking.booking.dto.UserResponse;
import com.example.hotelbooking.booking.dto.UserUpdateRequest;
import com.example.hotelbooking.booking.security.UserPrincipal;
import com.example.hotelbooking.booking.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/auth")
    public AuthResponse authenticate(@RequestBody AuthRequest request) {
        return userService.authenticate(request);
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getById(principal.getId());
    }

    @GetMapping
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody AdminUserRequest request) {
        return userService.createUser(request);
    }

    @PatchMapping
    public UserResponse update(@RequestBody UserUpdateRequest request) {
        return userService.updateUser(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam("id") Long id) {
        userService.deleteUser(id);
    }
}
