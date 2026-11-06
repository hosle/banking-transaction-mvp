package com.example.banking.transaction.api;

import com.example.banking.error.ApplicationException;
import com.example.banking.error.ErrorCode;
import com.example.banking.transaction.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionHandler {

    private final TransactionService transactionService;

    public TransactionHandler(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse created = transactionService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/transactions/" + created.transactionId()))
                .body(created);
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse get(@PathVariable long transactionId) {
        requirePositiveId(transactionId);
        return transactionService.get(transactionId);
    }

    @GetMapping
    public TransactionPageResponse list(@Valid @ModelAttribute TransactionFilterRequest filter) {
        return transactionService.list(filter);
    }

    @PutMapping("/{transactionId}")
    public TransactionResponse update(
            @PathVariable long transactionId,
            @Valid @RequestBody TransactionUpdateRequest request) {
        requirePositiveId(transactionId);
        return transactionService.update(transactionId, request);
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> delete(@PathVariable long transactionId) {
        requirePositiveId(transactionId);
        transactionService.softDelete(transactionId);
        return ResponseEntity.noContent().build();
    }

    private static void requirePositiveId(long transactionId) {
        if (transactionId <= 0) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    "transactionId must be greater than 0");
        }
    }
}
