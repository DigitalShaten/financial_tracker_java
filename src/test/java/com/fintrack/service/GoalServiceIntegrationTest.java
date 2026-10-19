package com.fintrack.service;

import com.fintrack.dto.GoalDetail;
import com.fintrack.dto.GoalForm;
import com.fintrack.dto.GoalPlanForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.Goal;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private GoalService goalService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private DashboardService dashboardService;

    private User anna;
    private YearMonth month;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        month = YearMonth.from(transactionService.today(anna.getId()));
    }

    private Goal laptop() {
        GoalForm form = new GoalForm();
        form.setName("Новый ноутбук");
        form.setPrice(new BigDecimal("1200"));
        form.setYears(1);
        form.setInflation(BigDecimal.ZERO);
        form.setRate(BigDecimal.ZERO);
        return goalService.create(anna.getId(), form);
    }

    @Test
    void newGoalTargetsFirstDayOfMonthAfterYears() {
        Goal goal = laptop();

        assertThat(goal.getTargetDate()).isEqualTo(month.plusMonths(12).atDay(1));
        assertThat(goal.isFamilyGoal()).isFalse();
    }

    @Test
    void contributionIncreasesSavedAmount() {
        Goal goal = laptop();

        goalService.contribute(anna.getId(), goal.getId(), new BigDecimal("640"), month.atDay(1));
        goalService.contribute(anna.getId(), goal.getId(), new BigDecimal("60"), month.atDay(1));

        assertThat(goalService.saved(goal.getId())).isEqualByComparingTo("700");
        GoalDetail detail = goalService.detail(anna.getId(), goal.getId());
        assertThat(detail.savedText()).isEqualTo("700 BYN");
        // (1200 - 700) / 12 = 41,67 -> "42"
        assertThat(detail.paymentText()).isEqualTo("42");
        assertThat(detail.contributors()).singleElement().satisfies(c -> assertThat(c.percent()).isEqualTo(100));
    }

    @Test
    void planUpdateChangesParameters() {
        Goal goal = laptop();
        GoalPlanForm plan = new GoalPlanForm();
        plan.setPrice(new BigDecimal("2400"));
        plan.setYears(2);
        plan.setInflation(new BigDecimal("4"));
        plan.setRate(new BigDecimal("6.5"));

        goalService.updatePlan(anna.getId(), goal.getId(), plan);

        Goal saved = goalService.getVisible(anna.getId(), goal.getId());
        assertThat(saved.getTargetAmount()).isEqualByComparingTo("2400");
        assertThat(saved.getTargetDate()).isEqualTo(month.plusMonths(24).atDay(1));
        assertThat(saved.getAnnualRate()).isEqualByComparingTo("6.5");
    }

    @Test
    void otherUsersPersonalGoalIsNotFound() {
        Goal goal = laptop();
        User boris = fixtures.createUser("Борис", "boris@test.by");

        assertThatThrownBy(() -> goalService.getVisible(boris.getId(), goal.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> goalService.contribute(boris.getId(), goal.getId(), BigDecimal.TEN, LocalDate.now()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> goalService.delete(boris.getId(), goal.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void familyGoalRequiresFamily() {
        GoalForm form = new GoalForm();
        form.setName("Квартира");
        form.setPrice(new BigDecimal("95000"));
        form.setYears(5);
        form.setFamily(true);

        assertThatThrownBy(() -> goalService.create(anna.getId(), form)).isInstanceOf(BusinessException.class);
    }

    @Test
    void freeCashIsAverageOfThreeFullMonths() {
        Account card = fixtures.createAccount(anna, "Карта", "0", true);
        for (int i = 1; i <= 3; i++) {
            fixtures.income(anna, card, "Зарплата", "2000", month.minusMonths(i).atDay(3));
            fixtures.expense(anna, card, "Расходы", "1100", "Продукты", month.minusMonths(i).atDay(5));
        }
        fixtures.income(anna, card, "Зарплата за текущий месяц не считается", "5000", month.atDay(1));

        assertThat(goalService.freeCash(anna.getId(), false)).isEqualByComparingTo("900.00");
    }

    @Test
    void plannedSavingsReduceDailyAllowance() {
        Account card = fixtures.createAccount(anna, "Карта", "0", true);
        fixtures.income(anna, card, "Зарплата", "3000", month.atDay(1));
        String before = dashboardService.build(anna.getId(), ViewMode.me(), month).todayText();

        laptop(); // взнос 100 в месяц

        assertThat(goalService.plannedMonthlySavings(anna.getId(), ViewMode.me())).isEqualByComparingTo("100.00");
        String after = dashboardService.build(anna.getId(), ViewMode.me(), month).todayText();
        assertThat(after).isNotEqualTo(before);
        assertThat(dashboardService.build(anna.getId(), ViewMode.me(), month).goals())
                .singleElement().satisfies(g -> assertThat(g.name()).isEqualTo("Новый ноутбук"));
    }
}
