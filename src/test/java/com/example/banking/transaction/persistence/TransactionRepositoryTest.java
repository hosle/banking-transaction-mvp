package com.example.banking.transaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.banking.account.Account;
import com.example.banking.account.AccountRepository;
import com.example.banking.account.AccountStatus;
import com.example.banking.transaction.api.TransactionFilterRequest;
import com.example.banking.transaction.domain.BankTransaction;
import com.example.banking.transaction.domain.TransactionCategory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account1;
    private Account account2;
    private Account account3;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        account1 = accountRepository.save(new Account("ACC-001", "CURRENT", AccountStatus.ACTIVE));
        account2 = accountRepository.save(new Account("ACC-002", "SAVINGS", AccountStatus.ACTIVE));
        account3 = accountRepository.save(new Account("ACC-003", "CURRENT", AccountStatus.ACTIVE));
    }

    @Test
    void enforcesUniqueClientReferenceEvenAfterSoftDelete() {
        BankTransaction original = save("UNIQUE-REF", account1, account2, 100L, "SGD", TransactionCategory.TRANSFER);
        original.softDelete(1_000L);
        transactionRepository.saveAndFlush(original);

        BankTransaction duplicate = transaction(
                "UNIQUE-REF", account2, account3, 200L, "USD", TransactionCategory.OTHER);

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountFilterMatchesSenderOrRecipientAndExcludesOtherAccounts() {
        BankTransaction sent = save("SENT", account1, account2, 100L, "SGD", TransactionCategory.TRANSFER);
        BankTransaction received = save("RECEIVED", account3, account1, 200L, "USD", TransactionCategory.OTHER);
        save("UNRELATED", account2, account3, 300L, "SGD", TransactionCategory.TRANSFER);

        TransactionFilterRequest filter = new TransactionFilterRequest();
        filter.setAccountId("ACC-001");
        Page<BankTransaction> result = transactionRepository.findAll(
                TransactionSpecification.from(filter), sortedPage(0, 20));

        assertThat(result.getContent())
                .extracting(BankTransaction::getTransactionId)
                .containsExactly(received.getTransactionId(), sent.getTransactionId());
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void combinesInclusiveFromExclusiveToCurrencyAndCategoryFilters() {
        BankTransaction atInclusiveFrom = save(
                "FROM-INCLUSIVE", account1, account2, 1_000L, "CNY", TransactionCategory.BILL);
        save("BEFORE", account1, account2, 999L, "CNY", TransactionCategory.BILL);
        save("AT-EXCLUSIVE-TO", account1, account2, 2_000L, "CNY", TransactionCategory.BILL);
        save("WRONG-CURRENCY", account1, account2, 1_500L, "USD", TransactionCategory.BILL);
        save("WRONG-CATEGORY", account1, account2, 1_500L, "CNY", TransactionCategory.SHOPPING);

        TransactionFilterRequest filter = new TransactionFilterRequest();
        filter.setFrom(1_000L);
        filter.setTo(2_000L);
        filter.setCurrency("CNY");
        filter.setCategory(TransactionCategory.BILL);

        List<BankTransaction> result = transactionRepository.findAll(
                TransactionSpecification.from(filter),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("transactionId")));

        assertThat(result).extracting(BankTransaction::getTransactionId)
                .containsExactly(atInclusiveFrom.getTransactionId());
    }

    @Test
    void softDeletedRowRemainsStoredButIsInvisibleToVisibleGetAndList() {
        BankTransaction deleted = save(
                "SOFT-DELETED", account1, account2, 1_000L, "HKD", TransactionCategory.OTHER);
        deleted.softDelete(2_000L);
        transactionRepository.saveAndFlush(deleted);

        assertThat(transactionRepository.findById(deleted.getTransactionId()))
                .get()
                .extracting(BankTransaction::getDeletedAt)
                .isEqualTo(2_000L);
        assertThat(transactionRepository.findByTransactionIdAndDeletedAtIsNull(deleted.getTransactionId()))
                .isEmpty();

        TransactionFilterRequest filter = new TransactionFilterRequest();
        assertThat(transactionRepository.findAll(TransactionSpecification.from(filter))).isEmpty();
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void returnsStableNewestFirstPagesAndMetadata() {
        BankTransaction oldest = save("OLDEST", account1, account2, 100L, "SGD", TransactionCategory.OTHER);
        BankTransaction sameTimeFirst = save("SAME-1", account1, account2, 200L, "SGD", TransactionCategory.OTHER);
        BankTransaction sameTimeSecond = save("SAME-2", account1, account2, 200L, "SGD", TransactionCategory.OTHER);

        TransactionFilterRequest filter = new TransactionFilterRequest();
        Page<BankTransaction> firstPage = transactionRepository.findAll(
                TransactionSpecification.from(filter), sortedPage(0, 2));
        Page<BankTransaction> secondPage = transactionRepository.findAll(
                TransactionSpecification.from(filter), sortedPage(1, 2));

        assertThat(firstPage.getContent())
                .extracting(BankTransaction::getTransactionId)
                .containsExactly(sameTimeSecond.getTransactionId(), sameTimeFirst.getTransactionId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();
        assertThat(secondPage.getContent())
                .extracting(BankTransaction::getTransactionId)
                .containsExactly(oldest.getTransactionId());
        assertThat(secondPage.isLast()).isTrue();
    }

    private BankTransaction save(
            String reference,
            Account sender,
            Account recipient,
            long occurredAt,
            String currency,
            TransactionCategory category) {
        return transactionRepository.saveAndFlush(
                transaction(reference, sender, recipient, occurredAt, currency, category));
    }

    private static BankTransaction transaction(
            String reference,
            Account sender,
            Account recipient,
            long occurredAt,
            String currency,
            TransactionCategory category) {
        return BankTransaction.create(
                reference,
                sender,
                recipient,
                new BigDecimal("10.0000"),
                currency,
                category,
                reference,
                occurredAt,
                10L);
    }

    private static PageRequest sortedPage(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("transactionId")));
    }
}
