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

    public Account getAccountById(Long id, String callerUsername) {
        AppUser caller = userRepository.findByUsername(callerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (!account.getOwner().getId().equals(caller.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You don't own this account");
        }
        return account;
    }
}
