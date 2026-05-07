package com.example.wallet.service;

import com.example.wallet.dto.LoginRequest;
import com.example.wallet.dto.LoginResponse;
import com.example.wallet.model.AppUser;
import com.example.wallet.repository.UserRepository;
import com.example.wallet.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    public static void main(String[] args) {
        var enc = new BCryptPasswordEncoder(12);
        System.out.println("password123 -> " + enc.encode("password123"));
        System.out.println("hunter2     -> " + enc.encode("hunter2"));
        System.out.println("ch@rlie!pass-> " + enc.encode("ch@rlie!pass"));
        System.out.println("admin123    -> " + enc.encode("admin123"));
    }
}
