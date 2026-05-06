package com.example.wallet.controller;

import com.example.wallet.model.Transaction;
import com.example.wallet.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * GET /api/transactions/account/{accountId}
     * ⚠️ VULNERABILITY: No ownership check — caller can read any account's transactions
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transaction>> getTransactions(@PathVariable Long accountId) {
        return ResponseEntity.ok(transactionService.getTransactionsForAccount(accountId));
    }

    /**
     * GET /api/transactions/account/{accountId}/search?keyword=coffee
     * ⚠️ VULNERABILITY: Missing ownership check allows cross-account data access
     */
    @GetMapping("/account/{accountId}/search")
    public ResponseEntity<List<Transaction>> searchTransactions(
            @PathVariable Long accountId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(transactionService.searchTransactions(accountId, keyword));
    }
}
