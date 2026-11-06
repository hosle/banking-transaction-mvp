package com.example.banking.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "account_id", length = 32, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "account_type", length = 20, nullable = false)
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AccountStatus status;

    protected Account() {
    }

    public Account(String accountId, String accountType, AccountStatus status) {
        this.accountId = accountId;
        this.accountType = accountType;
        this.status = status;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
