package com.example.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    // ⚠️ VULNERABILITY: Password hash returned in profile response
    private String password;
    private String role;
}
