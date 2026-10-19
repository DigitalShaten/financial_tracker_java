package com.fintrack.controller;

import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.support.IntegrationTest;
import com.fintrack.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

class AccountAccessIntegrationTest extends IntegrationTest {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;

    private User anna;
    private User boris;
    private Account borisCard;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        boris = fixtures.createUser("Борис", "boris@test.by");
        borisCard = fixtures.createAccount(boris, "Карта Бориса", "100", true);
    }

    @Test
    void accountsPageShowsOwnAccounts() throws Exception {
        fixtures.createAccount(anna, "Зарплатная карта", "1286.40", true);

        mvc.perform(get("/accounts").with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/index"))
                .andExpect(content().string(containsString("Зарплатная карта")))
                .andExpect(content().string(containsString("1 286,40 BYN")))
                .andExpect(content().string(not(containsString("Карта Бориса"))));
    }

    @Test
    void accountIsCreatedFromForm() throws Exception {
        mvc.perform(post("/accounts").with(user(fixtures.principalOf(anna))).with(csrf())
                        .param("name", "Копилка").param("type", "SAVINGS")
                        .param("initialBalance", "1 500,50").param("sharedWithFamily", "false"))
                .andExpect(redirectedUrl("/accounts"));

        assertThat(accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(anna.getId()))
                .filteredOn(a -> a.getName().equals("Копилка"))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getBalance()).isEqualByComparingTo("1500.50");
                    assertThat(a.isSharedWithFamily()).isFalse();
                });
    }

    @Test
    void invalidAccountFormShowsErrorsOnSamePage() throws Exception {
        mvc.perform(post("/accounts").with(user(fixtures.principalOf(anna))).with(csrf())
                        .param("name", "").param("type", "CARD").param("initialBalance", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/index"))
                .andExpect(content().string(containsString("Введите название счёта")));
    }

    @Test
    void foreignAccountVisibilityIsNotFound() throws Exception {
        mvc.perform(post("/accounts/{id}/visibility", borisCard.getId())
                        .with(user(fixtures.principalOf(anna))).with(csrf()).param("shared", "false"))
                .andExpect(status().isNotFound());

        assertThat(accountRepository.findById(borisCard.getId()).orElseThrow().isSharedWithFamily()).isTrue();
    }

    @Test
    void foreignAccountChangesAreNotFound() throws Exception {
        var anna = fixtures.principalOf(this.anna);
        mvc.perform(post("/accounts/{id}", borisCard.getId()).with(user(anna)).with(csrf())
                .param("name", "Взлом").param("type", "CARD")).andExpect(status().isNotFound());
        mvc.perform(post("/accounts/{id}/archive", borisCard.getId()).with(user(anna)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/accounts/{id}/delete", borisCard.getId()).with(user(anna)).with(csrf()))
                .andExpect(status().isNotFound());

        Account saved = accountRepository.findById(borisCard.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Карта Бориса");
        assertThat(saved.isArchived()).isFalse();
    }

    @Test
    void standardCategoryCannotBeRenamed() throws Exception {
        Long food = fixtures.standardCategory("Продукты").getId();

        mvc.perform(post("/categories/{id}", food).with(user(fixtures.principalOf(anna))).with(csrf())
                        .param("name", "Еда"))
                .andExpect(status().isNotFound());
    }

    @Test
    void profilePageAndDeletionWithLogout() throws Exception {
        mvc.perform(get("/profile").with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk()).andExpect(view().name("profile/index"));

        mvc.perform(post("/profile/delete").with(user(fixtures.principalOf(anna))).with(csrf())
                        .param("password", TestFixtures.PASSWORD))
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(userRepository.existsById(anna.getId())).isFalse();
    }

    @Test
    void wrongPasswordOnDeletionShowsError() throws Exception {
        mvc.perform(post("/profile/delete").with(user(fixtures.principalOf(anna))).with(csrf())
                        .param("password", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Неверный пароль")));
    }
}
