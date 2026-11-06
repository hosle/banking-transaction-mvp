package com.example.banking.transaction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.banking.account.Account;
import com.example.banking.account.AccountRepository;
import com.example.banking.account.AccountStatus;
import com.example.banking.transaction.domain.BankTransaction;
import com.example.banking.transaction.domain.TransactionCategory;
import com.example.banking.transaction.persistence.TransactionRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TransactionApiIntegrationTest {

    private static final long OCCURRED_AT = 1_700_000_000_000L;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        accountRepository.saveAll(List.of(
                new Account("ACC-001", "CURRENT", AccountStatus.ACTIVE),
                new Account("ACC-002", "SAVINGS", AccountStatus.ACTIVE),
                new Account("ACC-003", "CURRENT", AccountStatus.ACTIVE),
                new Account("ACC-999", "CURRENT", AccountStatus.INACTIVE)));
    }

    @Test
    void supportsCreateGetUpdateAndSoftDeleteLifecycle() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("API-LIFECYCLE", "ACC-001", "ACC-002", "100.00", "SGD")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/v1/transactions/\\d+")))
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andExpect(jsonPath("$.clientReference").value("API-LIFECYCLE"))
                .andExpect(jsonPath("$.senderAccountId").value("ACC-001"))
                .andExpect(jsonPath("$.recipientAccountId").value("ACC-002"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("SGD"))
                .andExpect(jsonPath("$.occurredAt").value(OCCURRED_AT))
                .andExpect(jsonPath("$.createdAt").isNumber())
                .andExpect(jsonPath("$.updatedAt").isNumber())
                .andReturn();
        long transactionId = transactionId(createResult);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientReference").value("API-LIFECYCLE"));

        mockMvc.perform(put("/api/v1/transactions/{id}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("125.50", "HKD", "Corrected")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.clientReference").value("API-LIFECYCLE"))
                .andExpect(jsonPath("$.senderAccountId").value("ACC-001"))
                .andExpect(jsonPath("$.recipientAccountId").value("ACC-002"))
                .andExpect(jsonPath("$.amount").value(125.50))
                .andExpect(jsonPath("$.currency").value("HKD"))
                .andExpect(jsonPath("$.description").value("Corrected"));

        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        BankTransaction storedRow = transactionRepository.findById(transactionId).orElseThrow();
        assertThat(storedRow.getDeletedAt()).isNotNull();
        assertThat(transactionRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("TXN_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listsAndPaginatesAccountMatchesInEitherParticipantColumn() throws Exception {
        long sentId = create("SENT-BY-1", "ACC-001", "ACC-002", "10.00", "SGD");
        long receivedId = create("RECEIVED-BY-1", "ACC-003", "ACC-001", "20.00", "SGD");
        create("UNRELATED", "ACC-002", "ACC-003", "30.00", "SGD");

        mockMvc.perform(get("/api/v1/transactions")
                        .param("accountId", "ACC-001")
                        .param("currency", "SGD")
                        .param("from", Long.toString(OCCURRED_AT))
                        .param("to", Long.toString(OCCURRED_AT + 1))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value(receivedId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/transactions")
                        .param("accountId", "ACC-001")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value(sentId))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void returnsOnlyActiveAccountsInStableOrder() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].accountId").value("ACC-001"))
                .andExpect(jsonPath("$[1].accountId").value("ACC-002"))
                .andExpect(jsonPath("$[2].accountId").value("ACC-003"));
    }

    @Test
    void returnsActionableValidationProblemForInvalidCreateBody() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("BAD-AMOUNT", "ACC-001", "ACC-002", "0", "SGD")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/transactions"))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')].message").isNotEmpty());
    }

    @Test
    void defensivelyRejectsUpdateAndDeleteWhenPersistedParticipantIsInactive() throws Exception {
        Account inactiveSender = accountRepository.findById("ACC-999").orElseThrow();
        Account activeRecipient = accountRepository.findById("ACC-002").orElseThrow();
        BankTransaction transaction = transactionRepository.saveAndFlush(BankTransaction.create(
                "DEFENSIVE-STATUS-CHECK",
                inactiveSender,
                activeRecipient,
                new BigDecimal("10.00"),
                "SGD",
                TransactionCategory.TRANSFER,
                "invalid persisted fixture",
                OCCURRED_AT,
                OCCURRED_AT));

        mockMvc.perform(put("/api/v1/transactions/{id}", transaction.getTransactionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("25.00", "USD", "must not update")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("senderAccountId 'ACC-999' is inactive")));

        mockMvc.perform(delete("/api/v1/transactions/{id}", transaction.getTransactionId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("senderAccountId 'ACC-999' is inactive")));

        BankTransaction unchanged = transactionRepository.findById(transaction.getTransactionId()).orElseThrow();
        assertThat(unchanged.getAmount()).isEqualByComparingTo("10.00");
        assertThat(unchanged.getDeletedAt()).isNull();
    }

    @Test
    void mapsBusinessFailuresToDocumentedProblemCodes() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("UNSUPPORTED", "ACC-001", "ACC-002", "10", "EUR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENCY_UNSUPPORTED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("EUR")));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("INACTIVE", "ACC-999", "ACC-002", "10", "SGD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ACC-999")));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("MISSING", "ACC-404", "ACC-002", "10", "SGD")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/transactions").param("accountId", "ACC-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsDuplicateReferenceMissingDeleteAndInvalidIds() throws Exception {
        create("DUPLICATE", "ACC-001", "ACC-002", "10", "USD");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("DUPLICATE", "ACC-002", "ACC-003", "20", "USD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TXN_DUPLICATE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("DUPLICATE")));

        mockMvc.perform(delete("/api/v1/transactions/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TXN_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/transactions/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("transactionId")));
    }

    @Test
    void rejectsInvalidPageSizeInvalidRangeAndMalformedJson() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'size')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/transactions").param("from", "20").param("to", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("from must be less than to")));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientReference\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void servesTheBrowserPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    private long create(
            String reference, String sender, String recipient, String amount, String currency) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(reference, sender, recipient, amount, currency)))
                .andExpect(status().isCreated())
                .andReturn();
        return transactionId(result);
    }

    private static long transactionId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
        return id.longValue();
    }

    private static String createJson(
            String reference, String sender, String recipient, String amount, String currency) {
        return """
                {
                  "clientReference": "%s",
                  "senderAccountId": "%s",
                  "recipientAccountId": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "category": "TRANSFER",
                  "description": "API test",
                  "occurredAt": %d
                }
                """.formatted(reference, sender, recipient, amount, currency, OCCURRED_AT);
    }

    private static String updateJson(String amount, String currency, String description) {
        return """
                {
                  "amount": %s,
                  "currency": "%s",
                  "category": "OTHER",
                  "description": "%s",
                  "occurredAt": %d
                }
                """.formatted(amount, currency, description, OCCURRED_AT);
    }
}
