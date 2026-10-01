package dev.akhileshaher.accountservice.controller;

import dev.akhileshaher.accountservice.dto.AccountResponse;
import dev.akhileshaher.accountservice.dto.CreateAccountRequest;
import dev.akhileshaher.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber
    ) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable String accountNumber
    ) {
        return ResponseEntity.ok(accountService.getBalance((accountNumber)));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(
            @PathVariable String accountNumber
    ) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked Successfully");
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request));
    }


/*
    SAGA STEP 1 : Deduct Balance
    Called by TransactionService when transfer is initiated
*/

    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ) {
        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance deducted Successfully");
    }

/*
    SAGA STEP 4 : Compensating Transaction endpoint
    Called by TransactionService in 2 SCENARIOS
    1. Fraud Detected -> refund sender (undo step 1)
    2. Transaction completed -> credit Transfer
*/

    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @RequestParam  BigDecimal amount
    ) {
        accountService.creditBalance(accountNumber,amount);
        return ResponseEntity.ok("BALANCE CREDITED SUCCESSFULLY");
    }


}
