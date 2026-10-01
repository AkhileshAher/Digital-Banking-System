package dev.akhileshaher.transactionservice.mapper;

import dev.akhileshaher.transactionservice.dto.TransactionResponse;
import dev.akhileshaher.transactionservice.entity.Transaction;

public class TransactionMapper {

    public static TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = TransactionResponse.builder()
                .id(transaction.getId())
                .senderAccountNumber(transaction.getSenderAccountNumber())
                .receiverAccountNumber(transaction.getReceiverAccountNumber())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .referenceNumber(transaction.getReferenceNumber())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
        return response;
    }

}
