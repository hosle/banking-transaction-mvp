package com.example.banking.transaction.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record TransactionPageResponse(
        List<TransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static TransactionPageResponse from(Page<TransactionResponse> result) {
        return new TransactionPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast());
    }
}
