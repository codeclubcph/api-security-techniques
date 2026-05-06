package com.example.wallet.service;

import com.example.wallet.model.Account;
import com.example.wallet.model.AppUser;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public List<Account> getMyAccounts(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return accountRepository.findByOwnerId(user.getId());
    }

    public Account getAccountById(Long id) {
        // ⚠️ VULNERABILITY: No ownership check — any authenticated user can access
        //    any account by guessing the ID (IDOR — Insecure Direct Object Reference)
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }
}
