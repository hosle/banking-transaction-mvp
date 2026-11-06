package com.example.banking.config;

import com.example.banking.account.Account;
import com.example.banking.account.AccountRepository;
import com.example.banking.account.AccountStatus;
import com.example.banking.transaction.api.CreateTransactionRequest;
import com.example.banking.transaction.domain.TransactionCategory;
import com.example.banking.transaction.persistence.TransactionRepository;
import com.example.banking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder {

    @Bean
    CommandLineRunner seedDemoData(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionService transactionService,
            Clock clock) {
        return args -> {
            if (accountRepository.count() == 0) {
                accountRepository.saveAll(List.of(
                        new Account("ACC-001", "CURRENT", AccountStatus.ACTIVE),
                        new Account("ACC-002", "SAVINGS", AccountStatus.ACTIVE),
                        new Account("ACC-003", "CURRENT", AccountStatus.ACTIVE),
                        new Account("ACC-999", "CURRENT", AccountStatus.INACTIVE)));
            }

            if (transactionRepository.count() == 0) {
                long now = clock.millis();
                transactionService.create(new CreateTransactionRequest(
                        "SEED-0001",
                        "ACC-001",
                        "ACC-002",
                        new BigDecimal("125.50"),
                        "SGD",
                        TransactionCategory.TRANSFER,
                        "Seed transfer",
                        now - 86_400_000L));
                transactionService.create(new CreateTransactionRequest(
                        "SEED-0002",
                        "ACC-002",
                        "ACC-003",
                        new BigDecimal("48.20"),
                        "HKD",
                        TransactionCategory.SHOPPING,
                        "Seed purchase",
                        now - 43_200_000L));
            }
        };
    }
}
