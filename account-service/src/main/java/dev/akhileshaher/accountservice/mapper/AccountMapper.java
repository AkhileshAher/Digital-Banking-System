package dev.akhileshaher.accountservice.mapper;

import dev.akhileshaher.accountservice.dto.AccountResponse;
import dev.akhileshaher.accountservice.entity.Account;

public class AccountMapper {

    public static AccountResponse mapToResponse(Account account) {
        AccountResponse response = AccountResponse.builder()
                .id(account.getId())
                .accountHolderName(account.getAccountHolderName())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .email(account.getEmail())
                .phone(account.getPhone())
                .status(account.getStatus())
                .dailyTransactionLimit(account.getDailyTransactionLimit())
                .createdAt(account.getCreatedAt())
                .build();
        return response;
    }

}
