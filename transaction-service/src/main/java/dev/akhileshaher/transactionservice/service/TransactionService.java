package dev.akhileshaher.transactionservice.service;

import dev.akhileshaher.transactionservice.client.AccountServiceClient;
import dev.akhileshaher.transactionservice.dto.TransactionResponse;
import dev.akhileshaher.transactionservice.dto.TransferRequest;
import dev.akhileshaher.transactionservice.entity.Transaction;
import dev.akhileshaher.transactionservice.entity.TransactionStatus;
import dev.akhileshaher.transactionservice.entity.TransactionType;
import dev.akhileshaher.transactionservice.event.TransactionInitiatedEvent;
import dev.akhileshaher.transactionservice.mapper.TransactionMapper;
import dev.akhileshaher.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static  final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static  final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static  final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    /**
     * SAGA STEP 1 : Inititate Transfer
     * Deducts from sender via feign
     * Saves transaction as PROCESSING
     * Publish event to kafka for fraud check
     * Returns
     * @param request
     * @return
     */
    public TransactionResponse transfer(TransferRequest request) {

        log.info("SAGA START - Transfer: {} -> {} amount: {}",request.getSenderAccountNumber(),request.getReceiverAccountNumber(),request.getAmount());

        // SAGA STEP 1 : Deduct FROM sender
        accountServiceClient.deductBalance(request.getSenderAccountNumber(),request.getAmount());

        Transaction transaction = Transaction.builder()
                .senderAccountNumber(request.getSenderAccountNumber())
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSING)
                .description(request.getDescription())
                .referenceNumber(UUID.randomUUID().toString())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}",savedTransaction.getId());

        // SAGA STEP 2 : Publish for fraud check
        TransactionInitiatedEvent event = TransactionInitiatedEvent.builder()
                .transactionId(savedTransaction.getId())
                .senderAccountNumber(savedTransaction.getSenderAccountNumber())
                .receiverAccountNumber(savedTransaction.getReceiverAccountNumber())
                .amount(savedTransaction.getAmount())
                .description(savedTransaction.getDescription())
                .build();

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(), event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}",savedTransaction.getId());

        return TransactionMapper.mapToResponse(savedTransaction);
    }

    public TransactionResponse getTransaction(String transactionId) {
        return TransactionMapper.mapToResponse(transactionRepository.findById(transactionId).orElseThrow(() -> new RuntimeException(
                "Transaction not found: " + transactionId
        )));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(trnx -> TransactionMapper.mapToResponse(trnx))
                .collect(Collectors.toList());
    }



}
