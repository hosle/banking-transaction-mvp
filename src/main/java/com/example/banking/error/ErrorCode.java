package com.example.banking.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    ACCOUNT_INACTIVE(HttpStatus.BAD_REQUEST, "Account inactive"),
    CURRENCY_UNSUPPORTED(HttpStatus.BAD_REQUEST, "Currency unsupported"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    TXN_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),
    TXN_DUPLICATE(HttpStatus.CONFLICT, "Duplicate transaction"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
