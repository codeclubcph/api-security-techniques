package com.example.wallet.controller;

import com.example.wallet.model.Transaction;
import com.example.wallet.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * GET /api/transactions/account/{accountId}
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transaction>> getTransactions(
            @PathVariable Long accountId, Authentication auth) {
        return ResponseEntity.ok(
                transactionService.getTransactionsForAccount(accountId, auth.getName()));
    }

    /**
     * GET /api/transactions/account/{accountId}/search?keyword=coffee
     */
    @GetMapping("/account/{accountId}/search")
    public ResponseEntity<List<Transaction>> searchTransactions(
            @PathVariable Long accountId,
            @RequestParam String keyword,
            Authentication auth) {
        return ResponseEntity.ok(transactionService.searchTransactions(accountId, keyword, auth.getName()));
    }
}
