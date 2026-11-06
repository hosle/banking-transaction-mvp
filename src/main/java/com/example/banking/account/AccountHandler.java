package com.example.banking.account;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountHandler {

    private final AccountRepository accountRepository;

    public AccountHandler(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<AccountResponse> listActiveAccounts() {
        return accountRepository.findAllByStatusOrderByAccountId(AccountStatus.ACTIVE)
                .stream()
                .map(AccountResponse::from)
                .toList();
    }
}
