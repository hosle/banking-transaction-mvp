package com.example.banking.transaction.persistence;

import com.example.banking.transaction.domain.BankTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
        extends JpaRepository<BankTransaction, Long>, JpaSpecificationExecutor<BankTransaction> {

    boolean existsByClientReference(String clientReference);

    Optional<BankTransaction> findByTransactionIdAndDeletedAtIsNull(Long transactionId);
}
