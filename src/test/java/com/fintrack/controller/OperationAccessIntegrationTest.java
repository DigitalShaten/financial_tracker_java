package com.fintrack.controller;

import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.TransactionService;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class OperationAccessIntegrationTest extends IntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionService transactionService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = fixtures.createUser("Алиса", "alice@test.by");
        bob = fixtures.createUser("Боб", "bob@test.by");
    }

    private String today() {
        return transactionService.today(alice.getId()).toString();
    }

    @Test
    void cannotOpenSomeoneElsesOperation() throws Exception {
        Long foreignTxId = fixtures.createExpenseFor(alice, "Супермаркет", "46.80");
        CustomUserDetails bob = fixtures.principalOf(this.bob);

        mvc.perform(get("/operations/{id}/edit", foreignTxId).with(user(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotDeleteSomeoneElsesOperation() throws Exception {
        Long foreignTxId = fixtures.createExpenseFor(alice, "Супермаркет", "46.80");

        mvc.perform(post("/operations/{id}/delete", foreignTxId)
                        .with(user(fixtures.principalOf(bob)))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.existsById(foreignTxId)).isTrue();
    }

    @Test
    void cannotChangeSomeoneElsesOperation() throws Exception {
        Long foreignTxId = fixtures.createExpenseFor(alice, "Супермаркет", "46.80");
        Account bobCash = accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(bob.getId()).get(0);

        mvc.perform(post("/operations/{id}", foreignTxId).with(user(fixtures.principalOf(bob))).with(csrf())
                        .param("type", "EXPENSE").param("title", "Взлом").param("amount", "1")
                        .param("accountId", bobCash.getId().toString())
                        .param("categoryId", fixtures.standardCategory("Продукты").getId().toString())
                        .param("operationDate", today()))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.findById(foreignTxId).orElseThrow().getTitle()).isEqualTo("Супермаркет");
    }

    @Test
    void cannotChangeVisibilityOfSomeoneElsesAccount() throws Exception {
        Account aliceCash = accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(alice.getId()).get(0);

        mvc.perform(post("/accounts/{id}/visibility", aliceCash.getId())
                        .with(user(fixtures.principalOf(bob))).with(csrf()).param("shared", "false"))
                .andExpect(status().isNotFound());
    }

    @Test
    void operationIsCreatedFromFormAndShownInList() throws Exception {
        Account cash = accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(alice.getId()).get(0);
        CustomUserDetails me = fixtures.principalOf(alice);

        mvc.perform(post("/operations").with(user(me)).with(csrf())
                        .param("type", "EXPENSE").param("title", "Супермаркет").param("amount", "46,80")
                        .param("accountId", cash.getId().toString())
                        .param("categoryId", fixtures.standardCategory("Продукты").getId().toString())
                        .param("operationDate", today()))
                .andExpect(redirectedUrl("/operations"));

        assertThat(accountRepository.findById(cash.getId()).orElseThrow().getBalance()).isEqualByComparingTo("-46.80");
        mvc.perform(get("/operations").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(view().name("operations/index"))
                .andExpect(content().string(containsString("Супермаркет")))
                .andExpect(content().string(containsString("− 46,80 BYN")))
                .andExpect(content().string(containsString("1 операция")));
    }

    @Test
    void invalidFormShowsErrorsOnSamePage() throws Exception {
        mvc.perform(post("/operations").with(user(fixtures.principalOf(alice))).with(csrf())
                        .param("type", "EXPENSE").param("title", "").param("amount", "abc")
                        .param("operationDate", today()))
                .andExpect(status().isOk())
                .andExpect(view().name("operations/index"))
                .andExpect(content().string(containsString("Введите название")))
                .andExpect(content().string(containsString("Введите число")));
    }

    @Test
    void transferToSameAccountShowsBusinessError() throws Exception {
        Account cash = accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(alice.getId()).get(0);

        mvc.perform(post("/operations").with(user(fixtures.principalOf(alice))).with(csrf())
                        .param("type", "TRANSFER").param("title", "Перевод").param("amount", "10")
                        .param("accountId", cash.getId().toString()).param("targetAccountId", cash.getId().toString())
                        .param("operationDate", today()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Выберите другой счёт для перевода")));
    }

    @Test
    void editPageShowsOwnOperation() throws Exception {
        Long id = fixtures.createExpenseFor(alice, "Аптека", "23.90");

        mvc.perform(get("/operations/{id}/edit", id).with(user(fixtures.principalOf(alice))))
                .andExpect(status().isOk())
                .andExpect(view().name("operations/edit"))
                .andExpect(content().string(containsString("value=\"Аптека\"")));
    }

    @Test
    void otherUsersOperationsAreNotListed() throws Exception {
        fixtures.createExpenseFor(alice, "Секретная покупка", "5");

        mvc.perform(get("/operations").with(user(fixtures.principalOf(bob))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Секретная покупка"))));
    }
}
