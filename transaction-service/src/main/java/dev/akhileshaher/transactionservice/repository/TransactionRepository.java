package dev.akhileshaher.transactionservice.repository;

import dev.akhileshaher.transactionservice.dto.TransactionResponse;
import dev.akhileshaher.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction,String> {
    List<Transaction> findBySenderAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
