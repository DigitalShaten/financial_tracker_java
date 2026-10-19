package com.fintrack.controller;

import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.support.IntegrationTest;
import com.fintrack.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPageIntegrationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User anna;
    private CustomUserDetails admin;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        fixtures.createAccount(anna, "Карта Анны", "1234.56", true);
        admin = fixtures.principalOf(fixtures.createAdmin("root@test.by"));
    }

    @Test
    void adminRootRedirectsToUsers() throws Exception {
        mvc.perform(get("/admin").with(user(admin))).andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    void usersPageShowsUsersAndHealthButNoMoney() throws Exception {
        mvc.perform(get("/admin/users").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("anna@test.by")))
                .andExpect(content().string(containsString("Сервис работает")))
                .andExpect(content().string(containsString("Пользователи")))
                .andExpect(content().string(not(containsString("1 234,56"))))
                .andExpect(content().string(not(containsString("Карта Анны"))));
    }

    @Test
    void blockedUserCannotLogInAndUnblockedCan() throws Exception {
        mvc.perform(post("/admin/users/{id}/block", anna.getId()).with(user(admin)).with(csrf()))
                .andExpect(flash().attribute("toast", "Пользователь заблокирован"));
        mvc.perform(formLogin("/login").userParameter("email").user("anna@test.by").password(TestFixtures.PASSWORD))
                .andExpect(unauthenticated());

        mvc.perform(post("/admin/users/{id}/unblock", anna.getId()).with(user(admin)).with(csrf()));
        assertThat(userRepository.findById(anna.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void adminCannotBlockHimself() throws Exception {
        mvc.perform(post("/admin/users/{id}/block", admin.id()).with(user(admin)).with(csrf()))
                .andExpect(flash().attribute("toast", "Нельзя заблокировать самого себя"));
        assertThat(userRepository.findById(admin.id()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void adminDoesNotSeeUserPages() throws Exception {
        for (String page : new String[]{"/dashboard", "/operations", "/accounts", "/goals", "/family", "/telegram", "/profile"}) {
            mvc.perform(get(page).with(user(admin))).andExpect(status().isForbidden());
        }
    }

    @Test
    void userDoesNotSeeAdminPages() throws Exception {
        CustomUserDetails me = fixtures.principalOf(anna);
        for (String page : new String[]{"/admin/users", "/admin/categories", "/admin/admins", "/actuator/info"}) {
            mvc.perform(get(page).with(user(me))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/users/{id}/block", anna.getId()).with(user(me)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoriesPageAndDuplicateError() throws Exception {
        mvc.perform(get("/admin/categories").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Продукты")))
                .andExpect(content().string(containsString("Зарплата")));

        mvc.perform(post("/admin/categories").with(user(admin)).with(csrf())
                        .param("name", "продукты").param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Такая стандартная категория уже есть")));
    }

    @Test
    void adminsPageCreatesAdmin() throws Exception {
        mvc.perform(post("/admin/admins").with(user(admin)).with(csrf())
                        .param("name", "Мария").param("email", "maria@test.by").param("password", "password1"))
                .andExpect(redirectedUrl("/admin/admins"));

        mvc.perform(get("/admin/admins").with(user(admin)))
                .andExpect(content().string(containsString("maria@test.by")))
                .andExpect(content().string(containsString("Это вы")));

        mvc.perform(post("/admin/admins").with(user(admin)).with(csrf())
                        .param("name", "Ещё").param("email", "maria@test.by").param("password", "password1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Этот e-mail уже занят")));
    }
}
