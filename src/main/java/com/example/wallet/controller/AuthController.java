package com.example.wallet.controller;

import com.example.wallet.dto.LoginRequest;
import com.example.wallet.dto.LoginResponse;
import com.example.wallet.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/login
     * ⚠️ VULNERABILITY: No rate limiting — unlimited brute-force attempts allowed
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
