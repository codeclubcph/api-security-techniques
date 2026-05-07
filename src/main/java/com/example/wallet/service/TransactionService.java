package com.example.wallet.service;

import com.example.wallet.model.Account;
import com.example.wallet.model.AppUser;
import com.example.wallet.model.Transaction;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public List<Transaction> getTransactionsForAccount(Long accountId, String callerUsername) {
        AppUser caller = userRepository.findByUsername(callerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (!account.getOwner().getId().equals(caller.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You don't own this account");
        }
        return transactionRepository.findByAccountId(accountId);
    }

    public List<Transaction> searchTransactions(Long accountId, String keyword, String callerUsername) {
        AppUser caller = userRepository.findByUsername(callerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (!account.getOwner().getId().equals(caller.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You don't own this account");
        }
        return transactionRepository.searchByDescription(accountId, keyword);
    }
}
