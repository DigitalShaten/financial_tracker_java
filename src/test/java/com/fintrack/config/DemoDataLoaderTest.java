package com.fintrack.config;

import com.fintrack.dto.BudgetRow;
import com.fintrack.entity.Account;
import com.fintrack.entity.Goal;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.GoalRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.service.AccessService;
import com.fintrack.service.BudgetService;
import com.fintrack.service.FamilyService;
import com.fintrack.service.GoalService;
import com.fintrack.service.TransactionService;
import com.fintrack.service.ViewMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Демо-данные создаются при старте с профилем {@code demo}. У теста своя база в памяти,
 * чтобы демо-пользователи не попали в базу остальных тестов.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fintrack-demo;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=MONTH")
@ActiveProfiles({"test", "demo"})
class DemoDataLoaderTest {

    @Autowired
    private DemoDataLoader loader;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private AccessService accessService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private GoalService goalService;

    @Autowired
    private TransactionService transactionService;

    private User dmitry;
    private User anna;

    @BeforeEach
    void setUp() {
        dmitry = userRepository.findByEmail(DemoDataLoader.DMITRY_EMAIL).orElseThrow();
        anna = userRepository.findByEmail(DemoDataLoader.ANNA_EMAIL).orElseThrow();
    }

    @Test
    void familyAndAccountsAreCreated() {
        assertThat(dmitry.getName()).isEqualTo("Дмитрий Иванов");
        assertThat(familyService.members(dmitry.getId())).hasSize(2);

        Map<String, Boolean> dmitryAccounts = accountRepository.findAllByUser_IdOrderByArchivedAscNameAsc(dmitry.getId())
                .stream().collect(Collectors.toMap(Account::getName, Account::isSharedWithFamily));
        assertThat(dmitryAccounts).containsOnly(Map.entry("Общий счёт", true), Map.entry("Зарплатная карта", true),
                Map.entry("Наличные", true), Map.entry("Личная копилка", false));
        assertThat(accountRepository.findAllByUser_IdOrderByArchivedAscNameAsc(anna.getId()))
                .extracting(Account::getName).containsExactlyInAnyOrder("Карта Анны", "Наличные");
    }

    @Test
    void aboutHundredFiftyOperationsWithoutNegativeBalances() {
        assertThat(transactionRepository.count()).isBetween(120L, 180L);
        assertThat(accountRepository.findAll()).allSatisfy(a -> assertThat(a.getBalance().signum()).isNotNegative());
    }

    @Test
    void cafeIsOverBudgetInCurrentMonth() {
        YearMonth month = YearMonth.from(transactionService.today(dmitry.getId()));

        for (User user : List.of(dmitry, anna)) {
            List<BudgetRow> rows = budgetService.rows(List.of(user.getId()),
                    accessService.visibleAccountIds(user.getId(), ViewMode.me()), month, "BYN");
            assertThat(rows).hasSize(6);
            assertThat(rows).filteredOn(BudgetRow::over).extracting(BudgetRow::categoryName)
                    .contains("Кафе и рестораны");
        }
    }

    @Test
    void goalsHaveContributionsFromSpecification() {
        List<Goal> goals = goalService.visibleGoals(dmitry.getId());
        Map<String, BigDecimal> saved = goals.stream()
                .collect(Collectors.toMap(Goal::getName, g -> goalService.saved(g.getId())));

        assertThat(saved.get("Квартира")).isEqualByComparingTo("18400");
        assertThat(saved.get("Отпуск летом 2027")).isEqualByComparingTo("1250");
        assertThat(saved.get("Новый ноутбук")).isEqualByComparingTo("640");
        // Анна видит обе семейные цели, но не личный ноутбук Дмитрия
        assertThat(goalService.visibleGoals(anna.getId())).extracting(Goal::getName)
                .containsExactlyInAnyOrder("Квартира", "Отпуск летом 2027");
    }

    @Test
    void secondRunDoesNotDuplicateData() {
        long users = userRepository.count();
        long operations = transactionRepository.count();
        long goals = goalRepository.count();

        loader.run(null);

        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(transactionRepository.count()).isEqualTo(operations);
        assertThat(goalRepository.count()).isEqualTo(goals);
    }
}
