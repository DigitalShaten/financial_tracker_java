package com.fintrack.service;

import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetAlertServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private BudgetAlertService alertService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private NotificationSettingsRepository settingsRepository;

    private User dmitry;
    private Account card;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
        card = fixtures.createAccount(dmitry, "Карта", "1000", true);
        today = transactionService.today(dmitry.getId());
        budgetService.setLimit(dmitry.getId(), fixtures.standardCategory("Кафе и рестораны").getId(),
                YearMonth.from(today), new BigDecimal("100"));
        fixtures.linkTelegram(dmitry, 1001L);
    }

    @Test
    void alertOnlyWhenLimitIsCrossed() {
        cafe("60");
        assertThat(telegram.messages()).isEmpty();

        cafe("50");   // 60 -> 110: лимит 100 пересечён
        assertThat(telegram.messages()).hasSize(1);
        assertThat(telegram.messages().get(0).text()).contains("«Кафе и рестораны»").contains("превышен");

        cafe("10");   // уже сверх лимита - второго сообщения нет
        assertThat(telegram.messages()).hasSize(1);
    }

    @Test
    void reachingLimitExactlyAlsoCounts() {
        cafe("100");

        assertThat(telegram.messages()).singleElement()
                .satisfies(m -> assertThat(m.text()).contains("исчерпан"));
    }

    @Test
    void noAlertWithoutBudgetOrWhenDisabled() {
        Long groceries = fixtures.expense(dmitry, card, "Супермаркет", "500", "Продукты", today).getId();
        alertService.check(groceries, dmitry.getId());
        assertThat(telegram.messages()).isEmpty();

        settingsRepository.findById(dmitry.getId()).orElseThrow().setBudgetAlerts(false);
        cafe("150");
        assertThat(telegram.messages()).isEmpty();
    }

    @Test
    void unlinkedUserGetsNothing() {
        User anna = fixtures.createUser("Анна", "anna@test.by");
        Account annaCard = fixtures.createAccount(anna, "Карта Анны", "500", true);
        budgetService.setLimit(anna.getId(), fixtures.standardCategory("Кафе и рестораны").getId(),
                YearMonth.from(today), new BigDecimal("10"));

        Long id = fixtures.expense(anna, annaCard, "Кофейня", "20", "Кафе и рестораны", today).getId();
        alertService.check(id, anna.getId());

        assertThat(telegram.messages()).isEmpty();
    }

    private void cafe(String amount) {
        Long id = fixtures.expense(dmitry, card, "Кофейня у работы", amount, "Кафе и рестораны", today).getId();
        alertService.check(id, dmitry.getId());
    }
}
