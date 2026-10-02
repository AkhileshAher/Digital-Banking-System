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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static  final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static  final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static  final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static  final String FRAUD_DETECTED_TOPIC = "fraud.detected";

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
                .map(TransactionMapper::mapToResponse)
                .collect(Collectors.toList());
    }


    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("OTP Verification fot the transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found " + transactionId
                ));

        String otpKey = "verification:otp" + transactionId;
        String storedOtp= redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null) {
            // OTP EXPIRED
            log.info("OTP expired for transaction: {}",transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled and amount recieved");
            return TransactionMapper.mapToResponse(transaction);
        }

        if(!storedOtp.equals(otp)) {
            // BLOCK ACCOUNT AND REFUND
            log.warn("Wrong OTP - blocking account and refunding: {}",transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                    "Wrong OTP entered - transaction cancelled, " +
                    "account blocked for security");

            return TransactionMapper.mapToResponse(transaction);
        }

        // OTP correct - complete transaction
        log.info("OTP verified - completing transaction: {}" ,transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return TransactionMapper.mapToResponse(transaction);
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());

        // CREDIT MONEY BACK TO SENDER SYNCRONOUSLY
        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(),transaction.getAmount());
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason +
                " - SAGA Compensation executed, amount refunded at " + LocalDateTime.now());

        transactionRepository.save(transaction);

        // PUBLISH refund event - Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);

        log.info("SAGA COMPENSATION COMPLETE - {} refunded to {}",
                transaction.getAmount(), transaction.getSenderAccountNumber());
    }


    private void blockAccountAndCompensate(Transaction transaction, String reason) {

        // Publish fraud.detected -> Account Service will block amount

        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId",transaction.getId());
        fraudEvent.put("accountNumber",transaction.getSenderAccountNumber());
        fraudEvent.put("reason",reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC,transaction.getSenderAccountNumber(),fraudEvent);
        log.warn("fraud.detected published - account: {} will be blocked, Kindly contact to the bank",
            transaction.getSenderAccountNumber());

        // SAGA COMPENSATION - refund Sender
        compensateTransaction(transaction,reason);
    }

    private void completeTransaction(Transaction transaction){

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(),completedEvent);
        log.info("SAGA COMPLETE - Transaction {} completed",
                transaction.getId());

    }

    public void processCleanTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found " + transactionId
                ));

        if(transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Transaction {} not PROCESSING - skipping", transactionId);
            return;
        }

        completeTransaction(transaction);

    }

}
