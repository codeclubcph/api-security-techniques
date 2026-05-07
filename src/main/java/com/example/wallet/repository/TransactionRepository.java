package com.example.wallet.repository;

import com.example.wallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountId(Long accountId);

    // ✅ FIX: JPQL with named parameters — type-safe, no raw SQL, no injection risk
    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId AND t.description LIKE %:keyword%")
    List<Transaction> searchByDescription(@Param("accountId") Long accountId, @Param("keyword") String keyword);
}
