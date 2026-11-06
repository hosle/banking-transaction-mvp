package com.example.banking.transaction.api;

import com.example.banking.transaction.domain.TransactionCategory;
import java.math.BigDecimal;

public record TransactionResponse(
        Long transactionId,
        String clientReference,
        String senderAccountId,
        String recipientAccountId,
        BigDecimal amount,
        String currency,
        TransactionCategory category,
        String description,
        long occurredAt,
        long createdAt,
        long updatedAt) {
}
