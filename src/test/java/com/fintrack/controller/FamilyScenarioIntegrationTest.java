package com.fintrack.controller;

import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.FamilyInviteRepository;
import com.fintrack.repository.FamilyMemberRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.BudgetService;
import com.fintrack.service.TransactionService;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.YearMonth;

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

class FamilyScenarioIntegrationTest extends IntegrationTest {

    @Autowired
    private FamilyInviteRepository inviteRepository;
    @Autowired
    private FamilyMemberRepository memberRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private BudgetService budgetService;

    private User dmitry;
    private User anna;
    private CustomUserDetails dmitryLogin;
    private CustomUserDetails annaLogin;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
        anna = fixtures.createUser("Анна", "anna@test.by");
        dmitryLogin = fixtures.principalOf(dmitry);
        annaLogin = fixtures.principalOf(anna);
    }

    private void createFamilyThroughPages() throws Exception {
        mvc.perform(post("/family").with(user(dmitryLogin)).with(csrf()).param("name", "Семья Ивановых"))
                .andExpect(redirectedUrl("/family"));
        mvc.perform(post("/family/invites").with(user(dmitryLogin)).with(csrf())).andExpect(redirectedUrl("/family"));
        String code = inviteRepository.findAll().get(0).getCode();
        mvc.perform(get("/family/join").param("code", code).with(user(annaLogin)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"" + code + "\"")));
        mvc.perform(post("/family/join").with(user(annaLogin)).with(csrf()).param("code", code.toLowerCase()))
                .andExpect(redirectedUrl("/family"));
    }

    @Test
    void fullFamilyScenario() throws Exception {
        createFamilyThroughPages();
        Account shared = fixtures.createAccount(dmitry, "Общий счёт", "1000", true);
        Account hidden = fixtures.createAccount(dmitry, "Личная копилка", "500", false);
        var month = YearMonth.from(transactionService.today(dmitry.getId()));
        fixtures.expense(dmitry, shared, "Супермаркет", "46.80", "Продукты", month.atDay(1));
        fixtures.expense(dmitry, hidden, "Подарок-сюрприз", "99", "Подарки", month.atDay(1));
        budgetService.setLimit(dmitry.getId(), fixtures.standardCategory("Продукты").getId(), month, new BigDecimal("270"));
        budgetService.setLimit(anna.getId(), fixtures.standardCategory("Продукты").getId(), month, new BigDecimal("230"));

        // Семья: участники, код приглашения только у владельца
        mvc.perform(get("/family").with(user(dmitryLogin)))
                .andExpect(content().string(containsString("Анна")))
                .andExpect(content().string(containsString("Пригласить в семью")));
        mvc.perform(get("/family").with(user(annaLogin)))
                .andExpect(content().string(containsString("Дмитрий")))
                .andExpect(content().string(not(containsString("Пригласить в семью"))))
                .andExpect(content().string(containsString("Выйти из семьи")));

        // Анна видит операции Дмитрия на открытом счёте, но не на скрытом, и без кнопок изменения
        mvc.perform(get("/operations").with(user(annaLogin)))
                .andExpect(content().string(containsString("Супермаркет")))
                .andExpect(content().string(not(containsString("Подарок-сюрприз"))))
                .andExpect(content().string(not(containsString("Удалить операцию «Супермаркет»"))))
                .andExpect(content().string(containsString("Вся семья")));

        // Обзор "Вместе": переключатель, бюджеты сложены по названию категории (270 + 230 = 500)
        mvc.perform(get("/dashboard").param("view", "all").with(user(annaLogin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Чьи данные показывать")))
                .andExpect(content().string(containsString("47 / 500 BYN")))
                .andExpect(content().string(containsString("Общая картина семьи")));
        // Режим "Дмитрий" для Анны
        mvc.perform(get("/dashboard").param("view", dmitry.getId().toString()).with(user(annaLogin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Операции на счетах, которые Дмитрий открывает для семьи")));
        // Подсказка видимости в форме операции у Дмитрия
        mvc.perform(get("/operations").with(user(dmitryLogin)))
                .andExpect(content().string(containsString("data-family-names=\"Анна\"")));
    }

    @Test
    void annaCannotTouchDmitrysHiddenAccountOrOperations() throws Exception {
        fixtures.family("Семья Ивановых", dmitry, anna);
        Account shared = fixtures.createAccount(dmitry, "Общий счёт", "1000", true);
        Account hidden = fixtures.createAccount(dmitry, "Личная копилка", "500", false);
        Long visibleTx = fixtures.expense(dmitry, shared, "Супермаркет", "46.80", "Продукты",
                transactionService.today(dmitry.getId())).getId();
        Long hiddenTx = fixtures.expense(dmitry, hidden, "Сюрприз", "99", "Подарки",
                transactionService.today(dmitry.getId())).getId();

        for (Long tx : new Long[]{visibleTx, hiddenTx}) {
            mvc.perform(get("/operations/{id}/edit", tx).with(user(annaLogin))).andExpect(status().isNotFound());
            mvc.perform(post("/operations/{id}/delete", tx).with(user(annaLogin)).with(csrf()))
                    .andExpect(status().isNotFound());
            assertThat(transactionRepository.existsById(tx)).isTrue();
        }
        for (Account acc : new Account[]{shared, hidden}) {
            mvc.perform(post("/accounts/{id}/visibility", acc.getId()).with(user(annaLogin)).with(csrf())
                    .param("shared", "true")).andExpect(status().isNotFound());
            mvc.perform(post("/accounts/{id}/archive", acc.getId()).with(user(annaLogin)).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/operations").param("accountId", hidden.getId().toString()).with(user(annaLogin)))
                .andExpect(status().isNotFound());
        assertThat(accountRepository.findById(hidden.getId()).orElseThrow().isSharedWithFamily()).isFalse();
    }

    @Test
    void ownerRemovesMemberAndDissolvesFamily() throws Exception {
        fixtures.family("Семья Ивановых", dmitry, anna);

        mvc.perform(post("/family/members/{id}/remove", anna.getId()).with(user(dmitryLogin)).with(csrf()))
                .andExpect(redirectedUrl("/family"));
        assertThat(memberRepository.findByUser_Id(anna.getId())).isEmpty();

        mvc.perform(post("/family/dissolve").with(user(dmitryLogin)).with(csrf())).andExpect(redirectedUrl("/family"));
        assertThat(memberRepository.findByUser_Id(dmitry.getId())).isEmpty();
        mvc.perform(get("/family").with(user(dmitryLogin))).andExpect(content().string(containsString("Создать семью")));
    }

    @Test
    void wrongCodeShowsError() throws Exception {
        mvc.perform(post("/family/join").with(user(annaLogin)).with(csrf()).param("code", "AAA-AAA"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Код недействителен или истёк")));
    }
}
