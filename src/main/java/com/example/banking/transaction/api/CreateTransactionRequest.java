package com.example.banking.transaction.api;

import com.example.banking.transaction.domain.TransactionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotBlank
                @Size(max = 64)
                @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must contain only letters, digits, '_' or '-'")
                String clientReference,
        @NotBlank @Size(max = 32) String senderAccountId,
        @NotBlank @Size(max = 32) String recipientAccountId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase code") String currency,
        TransactionCategory category,
        @Size(max = 140) String description,
        @NotNull @Positive Long occurredAt) {
}
