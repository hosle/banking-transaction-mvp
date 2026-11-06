package com.example.banking.transaction.persistence;

import com.example.banking.transaction.api.TransactionFilterRequest;
import com.example.banking.transaction.domain.BankTransaction;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecification {

    private TransactionSpecification() {
    }

    public static Specification<BankTransaction> from(TransactionFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));

            if (hasText(filter.getAccountId())) {
                Predicate sentByAccount = criteriaBuilder.equal(
                        root.get("senderAccount").get("accountId"), filter.getAccountId());
                Predicate receivedByAccount = criteriaBuilder.equal(
                        root.get("recipientAccount").get("accountId"), filter.getAccountId());
                predicates.add(criteriaBuilder.or(sentByAccount, receivedByAccount));
            }
            if (filter.getFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), filter.getFrom()));
            }
            if (filter.getTo() != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("occurredAt"), filter.getTo()));
            }
            if (hasText(filter.getCurrency())) {
                predicates.add(criteriaBuilder.equal(root.get("currency"), filter.getCurrency()));
            }
            if (filter.getCategory() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), filter.getCategory()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
