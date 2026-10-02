package dev.akhileshaher.transactionservice.service;

import dev.akhileshaher.transactionservice.entity.Transaction;
import dev.akhileshaher.transactionservice.entity.TransactionStatus;
import dev.akhileshaher.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    private final RedisTemplate<String,String> redisTemplate;
    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";
    private static final long OTP_EXPIRY_MINUTES = 5;

    /**
     * Consume verification.required
     * @param payload
     */
    @KafkaListener(topics = "verification.required")
    public void consumerVerificationRequired(
            @Payload Map<String, Object> payload
    ) {
            try {
                String transactionId = (String) payload.get("transactionId");
                String accountNumber = (String) payload.get("accountNumber");
                String reason = (String) payload.get("reason");

                log.info("Verification Required - transaction: {} reason: {}",transactionId,reason);

                Transaction transaction =transactionRepository.findById(transactionId)
                        .orElseThrow(() -> new RuntimeException(
                                "Transaction Not Found " + transactionId
                        ));

                if(transaction.getStatus() != TransactionStatus.PROCESSING) {
                    log.info("Transaction {} not PROCESSING - skipping", transactionId);
                    return;
                }

                // generate 6 digit OTP
                String otp = String.format("%06d",(int)(Math.random() * 900000) + 100000);

                // Store OTP in Redis - expires in 5 Minutes
                String otpKey = "verification:otp" + transactionId;
                redisTemplate.opsForValue().set(otpKey,otp,OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

                // Update status
                transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
                transactionRepository.save(transaction);

                log.info("OTP generated for transaction: {} expires in {} min",transactionId,OTP_EXPIRY_MINUTES);

                // Notify user
                Map<String, Object> otpEvent = new HashMap<>();
                otpEvent.put("transactionId",transactionId);
                otpEvent.put("accountNumber",accountNumber);
                otpEvent.put("reason",reason);
                otpEvent.put("otp",otp);
                otpEvent.put("amount",payload.get("amount"));

                kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId,otpEvent);

            } catch (Exception e) {
                log.error("Error Handling verification required: {}",e.getMessage());
            }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckClean(
            @Payload Map<String, Object> payload
    ) {

        try {

            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanTransaction(transactionId);

        } catch (Exception e) {
            log.error("Error processing fraud check result: {}", e.getMessage());
        }

    }

}
