package com.example.wallet.service;

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

    public List<Transaction> getTransactionsForAccount(Long accountId) {
        // ⚠️ VULNERABILITY: No check that the accountId belongs to the caller
        //    Authenticated user can read any account's transactions (IDOR)
        return transactionRepository.findByAccountId(accountId);
    }

    public List<Transaction> searchTransactions(Long accountId, String keyword) {
        // ⚠️ VULNERABILITY: Raw LIKE query — though parameterised here the exercise
        //    guide shows participants how to craft a payload that bypasses it via
        //    the missing account ownership check (all data accessible)
        return transactionRepository.searchByDescription(accountId, keyword);
    }
}
