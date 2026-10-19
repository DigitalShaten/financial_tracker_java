package com.fintrack.controller;

import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.support.IntegrationTest;
import com.fintrack.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;

    @Test
    void guestIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageIsOpen() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
    }

    @Test
    void guestSeesLandingPage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
    }

    @Test
    void userCannotOpenAdminSection() throws Exception {
        User anna = fixtures.createUser("Анна", "anna@test.by");

        mvc.perform(get("/admin/users").with(user(fixtures.principalOf(anna))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotOpenUserPages() throws Exception {
        User admin = fixtures.createAdmin("root@test.by");

        mvc.perform(get("/dashboard").with(user(fixtures.principalOf(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void homeRedirectsByRole() throws Exception {
        User anna = fixtures.createUser("Анна", "anna@test.by");
        User admin = fixtures.createAdmin("root@test.by");

        mvc.perform(get("/").with(user(fixtures.principalOf(anna)))).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/").with(user(fixtures.principalOf(admin)))).andExpect(redirectedUrl("/admin"));
    }

    @Test
    void activeUserLogsInWithEmailInAnyCase() throws Exception {
        fixtures.createUser("Анна", "anna@test.by");

        mvc.perform(formLogin("/login").userParameter("email").user("ANNA@test.by").password(TestFixtures.PASSWORD))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        fixtures.createUser("Анна", "anna@test.by");

        mvc.perform(formLogin("/login").userParameter("email").user("anna@test.by").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void blockedUserCannotLogIn() throws Exception {
        User anna = fixtures.createUser("Анна", "anna@test.by");
        anna.setEnabled(false);
        userRepository.saveAndFlush(anna);

        mvc.perform(formLogin("/login").userParameter("email").user("anna@test.by").password(TestFixtures.PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void registrationCreatesUserAndRedirectsToLogin() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Дмитрий")
                        .param("email", "Dmitry@Test.by")
                        .param("password", "password1")
                        .param("currency", "BYN"))
                .andExpect(redirectedUrl("/login?registered"));

        User created = userRepository.findByEmail("dmitry@test.by").orElseThrow();
        assertThat(accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(created.getId()))
                .extracting("name").containsExactly("Наличные");
    }

    @Test
    void registrationWithTakenEmailShowsFormAgain() throws Exception {
        fixtures.createUser("Анна", "anna@test.by");

        mvc.perform(post("/register").with(csrf())
                        .param("name", "Другая Анна")
                        .param("email", "anna@test.by")
                        .param("password", "password1")
                        .param("currency", "BYN"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/register").param("name", "X")).andExpect(status().isForbidden());
    }

    @Test
    void userSeesDashboardWithMenu() throws Exception {
        User anna = fixtures.createUser("Анна", "anna@test.by");

        mvc.perform(get("/dashboard").with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }
}
