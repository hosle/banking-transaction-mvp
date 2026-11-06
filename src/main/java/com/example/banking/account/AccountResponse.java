package com.example.banking.account;

public record AccountResponse(String accountId, String accountType) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.getAccountId(), account.getAccountType());
    }
}
