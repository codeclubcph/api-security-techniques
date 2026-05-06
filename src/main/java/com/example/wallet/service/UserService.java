package com.example.wallet.service;

import com.example.wallet.dto.UserResponse;
import com.example.wallet.model.AppUser;
import com.example.wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getProfile(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // ⚠️ VULNERABILITY: Password field returned in the response DTO
        return new UserResponse(user.getId(), user.getUsername(),
                user.getEmail(), user.getPassword(), user.getRole());
    }

    public UserResponse getUserById(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // ⚠️ VULNERABILITY: Any authenticated user can fetch any other user's profile
        //    including their plain-text password and role — no ownership check
        return new UserResponse(user.getId(), user.getUsername(),
                user.getEmail(), user.getPassword(), user.getRole());
    }
}
