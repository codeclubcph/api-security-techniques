package com.example.wallet.controller;

import com.example.wallet.model.Account;
import com.example.wallet.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** GET /api/accounts — returns only MY accounts (safe) */
    @GetMapping
    public ResponseEntity<List<Account>> getMyAccounts(Authentication auth) {
        return ResponseEntity.ok(accountService.getMyAccounts(auth.getName()));
    }

    /**
     * GET /api/accounts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccountById(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(accountService.getAccountById(id, auth.getName()));
    }
}
