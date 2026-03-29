package com.example.user_service.controller;

import com.example.user_service.dto.*;
import com.example.user_service.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth") // <--- CORRECT! (Gateway converts /user/auth -> /auth)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Endpoint: POST /user/auth/register (via Gateway)
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid SignupRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // Endpoint: POST /user/auth/login (via Gateway)
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @RequestBody @Valid RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleAuth(@RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.googleAuth(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @RequestHeader("X-User-Email") String email,
            @RequestBody @Valid LogoutRequest request) {
        return ResponseEntity.ok(authService.logout(request, email));
    }
}