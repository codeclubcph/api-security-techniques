package com.example.wallet.service;

import com.example.wallet.dto.LoginRequest;
import com.example.wallet.dto.LoginResponse;
import com.example.wallet.model.AppUser;
import com.example.wallet.repository.UserRepository;
import com.example.wallet.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // ⚠️ VULNERABILITY: Plain-text password comparison — no hashing
        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }
}
