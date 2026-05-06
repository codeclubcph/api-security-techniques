package com.example.wallet.repository;

import com.example.wallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountId(Long accountId);

    // ⚠️ VULNERABILITY: Native query using string concatenation — wide open to SQL injection
    // This is intentionally left as a raw query for the exercise
    @Query(value = "SELECT * FROM transaction WHERE account_id = ?1 AND description LIKE '%' || ?2 || '%'",
           nativeQuery = true)
    List<Transaction> searchByDescription(Long accountId, String keyword);
}
