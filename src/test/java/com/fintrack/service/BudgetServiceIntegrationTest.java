package com.fintrack.service;

import com.fintrack.dto.BudgetRow;
import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.repository.BudgetRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private BudgetService budgetService;
    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private TransactionService transactionService;

    private User anna;
    private Account card;
    private Long food;
    private Long cafe;
    private YearMonth month;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        card = fixtures.createAccount(anna, "Карта", "1000", true);
        food = fixtures.standardCategory("Продукты").getId();
        cafe = fixtures.standardCategory("Кафе и рестораны").getId();
        month = YearMonth.from(transactionService.today(anna.getId()));
    }

    private List<BudgetRow> rows() {
        return budgetService.rows(List.of(anna.getId()), Set.of(card.getId()), month, "BYN");
    }

    private LocalDate day() {
        return month.atDay(1);
    }

    @Test
    void settingLimitAgainUpdatesIt() {
        budgetService.setLimit(anna.getId(), food, month, new BigDecimal("300"));
        budgetService.setLimit(anna.getId(), food, month, new BigDecimal("270"));

        assertThat(budgetRepository.findAll()).singleElement()
                .satisfies(b -> assertThat(b.getLimitAmount()).isEqualByComparingTo("270"));
    }

    @Test
    void emptyLimitRemovesBudget() {
        budgetService.setLimit(anna.getId(), food, month, new BigDecimal("300"));
        budgetService.setLimit(anna.getId(), food, month, null);

        assertThat(budgetRepository.findAll()).isEmpty();
    }

    @Test
    void incomeCategoryCannotHaveBudget() {
        Long salary = fixtures.standardCategory("Зарплата").getId();

        assertThatThrownBy(() -> budgetService.setLimit(anna.getId(), salary, month, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void copiesBudgetsFromPreviousMonth() {
        budgetService.setLimit(anna.getId(), food, month.minusMonths(1), new BigDecimal("270"));
        budgetService.setLimit(anna.getId(), cafe, month.minusMonths(1), new BigDecimal("110"));
        budgetService.setLimit(anna.getId(), cafe, month, new BigDecimal("150"));

        int copied = budgetService.copyPrevious(anna.getId(), month);

        assertThat(copied).isEqualTo(1);
        assertThat(budgetRepository.findByUser_IdAndCategory_IdAndMonth(anna.getId(), food, month.atDay(1)))
                .get().satisfies(b -> assertThat(b.getLimitAmount()).isEqualByComparingTo("270"));
        assertThat(budgetRepository.findByUser_IdAndCategory_IdAndMonth(anna.getId(), cafe, month.atDay(1)))
                .get().satisfies(b -> assertThat(b.getLimitAmount()).isEqualByComparingTo("150"));
    }

    @Test
    void rowShowsRemainingAmount() {
        budgetService.setLimit(anna.getId(), food, month, new BigDecimal("500"));
        fixtures.expense(anna, card, "Супермаркет", "412", "Продукты", day());

        assertThat(rows()).singleElement().satisfies(r -> {
            assertThat(r.spentText()).isEqualTo("412");
            assertThat(r.limitText()).isEqualTo("500 BYN");
            assertThat(r.percent()).isEqualTo(82);
            assertThat(r.over()).isFalse();
            assertThat(r.note()).isEqualTo("Осталось 88 BYN");
        });
    }

    @Test
    void overspendingIsFlagged() {
        budgetService.setLimit(anna.getId(), cafe, month, new BigDecimal("200"));
        fixtures.expense(anna, card, "Кофейня", "236", "Кафе и рестораны", day());

        assertThat(rows()).singleElement().satisfies(r -> {
            assertThat(r.over()).isTrue();
            assertThat(r.percent()).isEqualTo(100);
            assertThat(r.note()).isEqualTo("Превышен на 18%");
        });
    }

    @Test
    void exactlySpentBudgetIsExhausted() {
        budgetService.setLimit(anna.getId(), food, month, new BigDecimal("320"));
        fixtures.expense(anna, card, "Коммуналка", "320", "Продукты", day());

        assertThat(rows()).singleElement().satisfies(r -> {
            assertThat(r.over()).isFalse();
            assertThat(r.note()).isEqualTo("Бюджет исчерпан");
        });
    }

    @Test
    void otherMonthExpensesDoNotCount() {
        budgetService.setLimit(anna.getId(), food, month, new BigDecimal("100"));
        fixtures.expense(anna, card, "Прошлый месяц", "90", "Продукты", month.minusMonths(1).atDay(5));

        assertThat(rows()).singleElement().satisfies(r -> assertThat(r.note()).isEqualTo("Осталось 100 BYN"));
    }
}
