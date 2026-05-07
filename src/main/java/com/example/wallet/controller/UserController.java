package com.example.wallet.controller;

import com.example.wallet.dto.UserResponse;
import com.example.wallet.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/users/me — returns the caller's own profile */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getProfile(auth.getName()));
    }

    /**
     * GET /api/users/{id}
     * ⚠️ VULNERABILITY: Any authenticated user can fetch any user's profile
     *    including their plain-text password. No ownership or admin check.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(userService.getUserById(id, auth.getName()));
    }
}
