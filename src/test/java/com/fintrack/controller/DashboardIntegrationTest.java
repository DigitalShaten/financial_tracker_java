package com.fintrack.controller;

import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.service.BudgetService;
import com.fintrack.service.TransactionService;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIntegrationTest extends IntegrationTest {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private BudgetService budgetService;

    private User anna;
    private Account card;
    private YearMonth month;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        card = fixtures.createAccount(anna, "Карта", "1000", true);
        month = YearMonth.from(transactionService.today(anna.getId()));
        fixtures.income(anna, card, "Зарплата", "1900", month.atDay(1));
        fixtures.expense(anna, card, "Кофейня у работы", "236", "Кафе и рестораны", month.atDay(1));
        budgetService.setLimit(anna.getId(), fixtures.standardCategory("Кафе и рестораны").getId(), month,
                new BigDecimal("200"));
    }

    @Test
    void dashboardShowsMonthTotalsBudgetsAndRecentOperations() throws Exception {
        mvc.perform(get("/dashboard").with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Добрый день, Анна")))
                .andExpect(content().string(containsString("2 664,00")))        // баланс 1000 + 1900 - 236
                .andExpect(content().string(containsString("1 900,00")))        // доходы
                .andExpect(content().string(containsString("12% от доходов")))
                .andExpect(content().string(containsString("Превышен на 18%")))
                .andExpect(content().string(containsString("Кофейня у работы")))
                .andExpect(content().string(containsString("До конца месяца")))
                .andExpect(content().string(containsString("Подключите Telegram-бота")))
                .andExpect(content().string(not(containsString("Чьи данные показывать"))));
    }

    @Test
    void previousMonthIsShownAsFinished() throws Exception {
        mvc.perform(get("/dashboard").param("month", month.minusMonths(1).toString())
                        .with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Месяц завершён")))
                .andExpect(content().string(containsString("Бюджеты не заданы")))
                .andExpect(content().string(not(containsString("Кофейня у работы"))));
    }

    @Test
    void invalidOrFutureMonthFallsBackToCurrent() throws Exception {
        mvc.perform(get("/dashboard").param("month", "abc").with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Превышен на 18%")));
        mvc.perform(get("/dashboard").param("month", month.plusMonths(3).toString()).with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Превышен на 18%")));
    }

    @Test
    void foreignMemberViewIsNotFound() throws Exception {
        User boris = fixtures.createUser("Борис", "boris@test.by");

        mvc.perform(get("/dashboard").param("view", boris.getId().toString()).with(user(fixtures.principalOf(anna))))
                .andExpect(status().isNotFound());
    }

    @Test
    void budgetIsSetAndCopiedFromAccountsPage() throws Exception {
        var me = fixtures.principalOf(anna);
        Long food = fixtures.standardCategory("Продукты").getId();

        mvc.perform(post("/budgets").with(user(me)).with(csrf())
                        .param("categoryId", food.toString()).param("month", month.toString()).param("limitAmount", "270"))
                .andExpect(redirectedUrl("/accounts?month=" + month + "#budgets"));
        mvc.perform(post("/budgets/copy-previous").with(user(me)).with(csrf()).param("month", month.plusMonths(1).toString()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/accounts").param("month", month.plusMonths(1).toString()).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"270\"")));
    }
}
