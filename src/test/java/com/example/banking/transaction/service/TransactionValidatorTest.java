package com.example.banking.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.banking.account.Account;
import com.example.banking.account.AccountRepository;
import com.example.banking.account.AccountStatus;
import com.example.banking.error.ApplicationException;
import com.example.banking.error.ErrorCode;
import com.example.banking.transaction.api.CreateTransactionRequest;
import com.example.banking.transaction.api.TransactionFilterRequest;
import com.example.banking.transaction.api.TransactionUpdateRequest;
import com.example.banking.transaction.domain.TransactionCategory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionValidatorTest {

    private static final long NOW = 1_800_000_000_000L;

    @Mock
    private AccountRepository accountRepository;

    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
        validator = new TransactionValidator(accountRepository, clock, "SGD,CNY,USD,HKD");
    }

    @Test
    void acceptsDistinctActiveAccountsAndSupportedCurrency() {
        Account sender = activeAccount("ACC-001");
        Account recipient = activeAccount("ACC-002");
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(sender));
        when(accountRepository.findById("ACC-002")).thenReturn(Optional.of(recipient));

        TransactionValidator.AccountPair pair = validator.validateCreate(createRequest(
                "ACC-001", "ACC-002", "SGD", NOW));

        assertThat(pair.sender()).isSameAs(sender);
        assertThat(pair.recipient()).isSameAs(recipient);
    }

    @Test
    void rejectsSameSenderAndRecipient() {
        assertApplicationError(
                () -> validator.validateCreate(createRequest("ACC-001", "ACC-001", "SGD", NOW)),
                ErrorCode.VALIDATION_FAILED,
                "must be different");
    }

    @Test
    void rejectsMissingAccount() {
        when(accountRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertApplicationError(
                () -> validator.validateCreate(createRequest("MISSING", "ACC-002", "SGD", NOW)),
                ErrorCode.ACCOUNT_NOT_FOUND,
                "senderAccountId 'MISSING' does not exist");
    }

    @Test
    void rejectsInactiveAccount() {
        when(accountRepository.findById("ACC-999"))
                .thenReturn(Optional.of(new Account("ACC-999", "CURRENT", AccountStatus.INACTIVE)));
        when(accountRepository.findById("ACC-002")).thenReturn(Optional.of(activeAccount("ACC-002")));

        assertApplicationError(
                () -> validator.validateCreate(createRequest("ACC-999", "ACC-002", "SGD", NOW)),
                ErrorCode.ACCOUNT_INACTIVE,
                "senderAccountId 'ACC-999' is inactive");
    }

    @Test
    void rejectsUnsupportedCurrencyOnCreateAndUpdate() {
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount("ACC-001")));
        when(accountRepository.findById("ACC-002")).thenReturn(Optional.of(activeAccount("ACC-002")));

        assertApplicationError(
                () -> validator.validateCreate(createRequest("ACC-001", "ACC-002", "EUR", NOW)),
                ErrorCode.CURRENCY_UNSUPPORTED,
                "EUR");

        TransactionUpdateRequest update = new TransactionUpdateRequest(
                new BigDecimal("25.00"), "EUR", TransactionCategory.OTHER, null, NOW);
        assertApplicationError(
                () -> validator.validateUpdate(update),
                ErrorCode.CURRENCY_UNSUPPORTED,
                "EUR");
    }

    @Test
    void rejectsFutureOccurrenceTime() {
        TransactionUpdateRequest update = new TransactionUpdateRequest(
                new BigDecimal("25.00"), "USD", null, null, NOW + 1);

        assertApplicationError(
                () -> validator.validateUpdate(update),
                ErrorCode.VALIDATION_FAILED,
                "must not be in the future");
    }

    @Test
    void rejectsInvalidRangeAndUnknownFilterAccount() {
        TransactionFilterRequest invalidRange = new TransactionFilterRequest();
        invalidRange.setFrom(200L);
        invalidRange.setTo(200L);
        assertApplicationError(
                () -> validator.validateFilter(invalidRange),
                ErrorCode.VALIDATION_FAILED,
                "from must be less than to");

        TransactionFilterRequest unknownAccount = new TransactionFilterRequest();
        unknownAccount.setAccountId("ACC-404");
        when(accountRepository.existsById("ACC-404")).thenReturn(false);
        assertApplicationError(
                () -> validator.validateFilter(unknownAccount),
                ErrorCode.ACCOUNT_NOT_FOUND,
                "ACC-404");
    }

    private static CreateTransactionRequest createRequest(
            String sender, String recipient, String currency, long occurredAt) {
        return new CreateTransactionRequest(
                "TEST-REF",
                sender,
                recipient,
                new BigDecimal("10.00"),
                currency,
                TransactionCategory.TRANSFER,
                "test",
                occurredAt);
    }

    private static Account activeAccount(String id) {
        return new Account(id, "CURRENT", AccountStatus.ACTIVE);
    }

    private static void assertApplicationError(
            Runnable invocation, ErrorCode expectedCode, String messageFragment) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(expectedCode);
                    assertThat(exception).hasMessageContaining(messageFragment);
                });
    }
}
