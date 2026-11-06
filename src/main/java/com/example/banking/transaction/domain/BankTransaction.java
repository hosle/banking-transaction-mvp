package com.example.banking.transaction.domain;

import com.example.banking.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(
        name = "bank_transaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transaction_client_reference",
                columnNames = "client_reference"),
        indexes = {
            @Index(name = "idx_txn_sender_occurred", columnList = "sender_account_id, occurred_at DESC"),
            @Index(name = "idx_txn_recipient_occurred", columnList = "recipient_account_id, occurred_at DESC"),
            @Index(name = "idx_txn_occurred_id", columnList = "occurred_at DESC, transaction_id DESC"),
            @Index(name = "idx_txn_deleted_occurred_id", columnList = "deleted_at, occurred_at DESC, transaction_id DESC")
        })
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "client_reference", length = 64, nullable = false, updatable = false)
    private String clientReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_account_id", nullable = false, updatable = false)
    private Account senderAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_account_id", nullable = false, updatable = false)
    private Account recipientAccount;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private TransactionCategory category;

    @Column(name = "description", length = 140)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private long occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "deleted_at")
    private Long deletedAt;

    protected BankTransaction() {
    }

    private BankTransaction(
            String clientReference,
            Account senderAccount,
            Account recipientAccount,
            BigDecimal amount,
            String currency,
            TransactionCategory category,
            String description,
            long occurredAt,
            long now) {
        this.clientReference = clientReference;
        this.senderAccount = senderAccount;
        this.recipientAccount = recipientAccount;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.description = description;
        this.occurredAt = occurredAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static BankTransaction create(
            String clientReference,
            Account senderAccount,
            Account recipientAccount,
            BigDecimal amount,
            String currency,
            TransactionCategory category,
            String description,
            long occurredAt,
            long now) {
        return new BankTransaction(
                clientReference,
                senderAccount,
                recipientAccount,
                amount,
                currency,
                category,
                description,
                occurredAt,
                now);
    }

    public void update(
            BigDecimal amount,
            String currency,
            TransactionCategory category,
            String description,
            long occurredAt,
            long now) {
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.description = description;
        this.occurredAt = occurredAt;
        this.updatedAt = now;
    }

    public void softDelete(long now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getClientReference() {
        return clientReference;
    }

    public Account getSenderAccount() {
        return senderAccount;
    }

    public Account getRecipientAccount() {
        return recipientAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public Long getDeletedAt() {
        return deletedAt;
    }
}
