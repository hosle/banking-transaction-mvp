package com.example.banking.transaction.service;

import com.example.banking.account.Account;
import com.example.banking.account.AccountRepository;
import com.example.banking.error.ApplicationException;
import com.example.banking.error.ErrorCode;
import com.example.banking.transaction.api.CreateTransactionRequest;
import com.example.banking.transaction.api.TransactionFilterRequest;
import com.example.banking.transaction.api.TransactionUpdateRequest;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TransactionValidator {

    private final AccountRepository accountRepository;
    private final Clock clock;
    private final Set<String> supportedCurrencies;

    public TransactionValidator(
            AccountRepository accountRepository,
            Clock clock,
            @Value("${app.transaction.supported-currencies:SGD,CNY,USD,HKD}") String currencies) {
        this.accountRepository = accountRepository;
        this.clock = clock;
        this.supportedCurrencies = Arrays.stream(currencies.split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    public AccountPair validateCreate(CreateTransactionRequest request) {
        if (request.senderAccountId().equals(request.recipientAccountId())) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED, "senderAccountId and recipientAccountId must be different");
        }

        Account sender = requireAccount(request.senderAccountId(), "senderAccountId");
        Account recipient = requireAccount(request.recipientAccountId(), "recipientAccountId");
        requireActive(sender, "senderAccountId");
        requireActive(recipient, "recipientAccountId");
        requireSupportedCurrency(request.currency());
        requireNotFuture(request.occurredAt());
        return new AccountPair(sender, recipient);
    }

    public void validateUpdate(TransactionUpdateRequest request) {
        requireSupportedCurrency(request.currency());
        requireNotFuture(request.occurredAt());
    }

    public void validateFilter(TransactionFilterRequest filter) {
        if (filter.getFrom() != null && filter.getTo() != null && filter.getFrom() >= filter.getTo()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "from must be less than to");
        }
        if (hasText(filter.getAccountId()) && !accountRepository.existsById(filter.getAccountId())) {
            throw new ApplicationException(
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account '%s' does not exist".formatted(filter.getAccountId()));
        }
        if (hasText(filter.getCurrency())) {
            requireSupportedCurrency(filter.getCurrency());
        }
    }

    private Account requireAccount(String accountId, String field) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "%s '%s' does not exist".formatted(field, accountId)));
    }

    private void requireActive(Account account, String field) {
        if (!account.isActive()) {
            throw new ApplicationException(
                    ErrorCode.ACCOUNT_INACTIVE,
                    "%s '%s' is inactive".formatted(field, account.getAccountId()));
        }
    }

    private void requireSupportedCurrency(String currency) {
        if (!supportedCurrencies.contains(currency)) {
            throw new ApplicationException(
                    ErrorCode.CURRENCY_UNSUPPORTED,
                    "currency '%s' is unsupported; accepted values are %s"
                            .formatted(currency, supportedCurrencies));
        }
    }

    private void requireNotFuture(long occurredAt) {
        if (occurredAt > clock.millis()) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    "occurredAt must not be in the future");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AccountPair(Account sender, Account recipient) {
    }
}
