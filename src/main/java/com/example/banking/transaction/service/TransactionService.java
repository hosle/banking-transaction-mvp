package com.example.banking.transaction.service;

import com.example.banking.error.ApplicationException;
import com.example.banking.error.ErrorCode;
import com.example.banking.transaction.api.CreateTransactionRequest;
import com.example.banking.transaction.api.TransactionFilterRequest;
import com.example.banking.transaction.api.TransactionPageResponse;
import com.example.banking.transaction.api.TransactionResponse;
import com.example.banking.transaction.api.TransactionUpdateRequest;
import com.example.banking.transaction.domain.BankTransaction;
import com.example.banking.transaction.persistence.TransactionRepository;
import com.example.banking.transaction.persistence.TransactionSpecification;
import com.example.banking.transaction.service.TransactionValidator.AccountPair;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionValidator validator;
    private final Clock clock;

    public TransactionService(
            TransactionRepository transactionRepository,
            TransactionValidator validator,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        String clientReference = request.clientReference().trim();
        if (transactionRepository.existsByClientReference(clientReference)) {
            throw duplicate(clientReference);
        }

        AccountPair accounts = validator.validateCreate(request);
        long now = clock.millis();
        BankTransaction transaction = BankTransaction.create(
                clientReference,
                accounts.sender(),
                accounts.recipient(),
                request.amount(),
                request.currency(),
                request.category(),
                normalizeOptional(request.description()),
                request.occurredAt(),
                now);
        try {
            return toResponse(transactionRepository.saveAndFlush(transaction));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(clientReference);
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(long transactionId) {
        return toResponse(findVisible(transactionId));
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse list(TransactionFilterRequest filter) {
        validator.validateFilter(filter);
        PageRequest pageable = PageRequest.of(
                filter.getPage(),
                filter.getSize(),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("transactionId")));
        Page<TransactionResponse> result = transactionRepository
                .findAll(TransactionSpecification.from(filter), pageable)
                .map(this::toResponse);
        return TransactionPageResponse.from(result);
    }

    @Transactional
    public TransactionResponse update(long transactionId, TransactionUpdateRequest request) {
        BankTransaction transaction = findVisible(transactionId);
        validator.validateUpdate(request);
        transaction.update(
                request.amount(),
                request.currency(),
                request.category(),
                normalizeOptional(request.description()),
                request.occurredAt(),
                clock.millis());
        return toResponse(transaction);
    }

    @Transactional
    public void softDelete(long transactionId) {
        BankTransaction transaction = findVisible(transactionId);
        transaction.softDelete(clock.millis());
    }

    private BankTransaction findVisible(long transactionId) {
        return transactionRepository.findByTransactionIdAndDeletedAtIsNull(transactionId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.TXN_NOT_FOUND,
                        "Transaction '%d' does not exist".formatted(transactionId)));
    }

    private TransactionResponse toResponse(BankTransaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getClientReference(),
                transaction.getSenderAccount().getAccountId(),
                transaction.getRecipientAccount().getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getOccurredAt(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ApplicationException duplicate(String clientReference) {
        return new ApplicationException(
                ErrorCode.TXN_DUPLICATE,
                "A transaction with clientReference '%s' already exists".formatted(clientReference));
    }
}
