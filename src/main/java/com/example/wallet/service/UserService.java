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
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public UserResponse getUserById(Long id, String callerUsername) {
        AppUser caller = userRepository.findByUsername(callerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getId().equals(caller.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You don't own this profile");
        }
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
