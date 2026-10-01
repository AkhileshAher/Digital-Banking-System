package dev.akhileshaher.accountservice.service;

import dev.akhileshaher.accountservice.Utils.AccountUtils;
import dev.akhileshaher.accountservice.dto.AccountResponse;
import dev.akhileshaher.accountservice.dto.CreateAccountRequest;
import dev.akhileshaher.accountservice.entity.Account;
import dev.akhileshaher.accountservice.entity.AccountStatus;
import dev.akhileshaher.accountservice.entity.AccountType;
import dev.akhileshaher.accountservice.mapper.AccountMapper;
import dev.akhileshaher.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create Account for User
     * @param request
     * @return
     */
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating Account for: {}",request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account Already Exists for email: " + request.getEmail());
        }

        Account account = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .status(AccountStatus.ACTIVE)
                .balance(request.getInitialDeposit())
                .accountNumber(generateAccountNumber())
                .dailyTransactionLimit(
                        request.getAccountType() == AccountType.SAVINGS
                        ? new BigDecimal("100000")
                                : new BigDecimal("500000")
                )
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Account created: {}",savedAccount.getAccountNumber());
        return AccountMapper.mapToResponse(savedAccount);
    }

    /**
     * Get account by account number
     * @param accountNumber
     * @return
     */
    public AccountResponse getAccount(String accountNumber) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        return AccountMapper.mapToResponse(account);
    }

    /**
     * Get Account Balance
     * @param accountNumber
     * @return
     */
    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));
        return account.getBalance();
    }


    /**
     * Block Account - called by Fraud Detection Service via kafka
     * @param accountNumber
     */
    public void blockAccount(String accountNumber) {
        log.info("Blocking account : {}",accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account Blocked: {}",accountNumber);
    }

    /**
     * Deduct balance from sender account
     * Called by Transaction Service
     * @param accountNumber
     * @param amount
     */
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting Balance: {} from account:{}",amount,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        if(account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        if(account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds for account " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance Updated, New Balance : {}", account.getBalance());
    }

    /**
     * Credit Balance
     * Called by Transaction Service via Kafka
     * @param accountNumber
     * @param amount
     */
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to Account to account: {}", amount, accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Balance Credited, New Balance: {}", account.getBalance());

    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d",number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;

    }

}
