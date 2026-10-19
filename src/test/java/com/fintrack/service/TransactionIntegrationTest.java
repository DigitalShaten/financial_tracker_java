package com.fintrack.service;

import com.fintrack.dto.OperationsPage;
import com.fintrack.dto.TransactionFilter;
import com.fintrack.dto.TransactionForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionIntegrationTest extends IntegrationTest {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private EntityManager entityManager;

    private User anna;
    private Account card;
    private Account cash;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        today = transactionService.today(anna.getId());
        card = fixtures.createAccount(anna, "Карта", "500", true);
        cash = accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(anna.getId()).stream()
                .filter(a -> a.getName().equals("Наличные")).findFirst().orElseThrow();
    }

    private BigDecimal balanceFromDb(Account account) {
        entityManager.flush();
        entityManager.clear();
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    @Test
    void transferIsStoredAndBalancesAreReread() {
        fixtures.operation(anna, TransactionType.TRANSFER, card, cash, "Снял наличные", "120.50", null, today);

        assertThat(balanceFromDb(card)).isEqualByComparingTo("379.50");
        assertThat(balanceFromDb(cash)).isEqualByComparingTo("120.50");
    }

    @Test
    void balanceEqualsInitialPlusAllOperations() {
        var groceries = fixtures.expense(anna, card, "Супермаркет", "46.80", "Продукты", today);
        fixtures.income(anna, card, "Зарплата", "1900", today);
        fixtures.operation(anna, TransactionType.TRANSFER, card, cash, "В кошелёк", "100", null, today);

        TransactionForm change = transactionService.formOf(anna.getId(), groceries.getId());
        change.setAmount(new BigDecimal("50.00"));
        transactionService.update(anna.getId(), groceries.getId(), change);

        // 500 - 50 + 1900 - 100
        assertThat(balanceFromDb(card)).isEqualByComparingTo("2250.00");
        assertThat(balanceFromDb(cash)).isEqualByComparingTo("100.00");

        transactionService.delete(anna.getId(), groceries.getId());
        assertThat(balanceFromDb(card)).isEqualByComparingTo("2300.00");
    }

    @Test
    void foreignAccountCannotBeUsed() {
        User boris = fixtures.createUser("Борис", "boris@test.by");
        Account borisCard = fixtures.createAccount(boris, "Карта Бориса", "0", true);

        assertThatThrownBy(() -> fixtures.expense(anna, borisCard, "Чужое", "10", "Продукты", today))
                .isInstanceOf(NotFoundException.class);
        assertThat(balanceFromDb(borisCard)).isEqualByComparingTo("0");
    }

    @Test
    void incomeWithExpenseCategoryIsRejected() {
        assertThatThrownBy(() -> fixtures.operation(anna, TransactionType.INCOME, card, null, "Странно", "10",
                "Продукты", today)).isInstanceOf(BusinessException.class);
        assertThat(balanceFromDb(card)).isEqualByComparingTo("500");
    }

    @Test
    void listIsFilteredAndTotalsUseSameFilter() {
        fixtures.expense(anna, card, "Супермаркет", "46.80", "Продукты", today);
        fixtures.expense(anna, card, "Такси", "12.40", "Транспорт", today);
        fixtures.income(anna, card, "Зарплата", "1900", today);
        fixtures.operation(anna, TransactionType.TRANSFER, card, cash, "В кошелёк", "100", null, today);
        fixtures.expense(anna, card, "Старая покупка", "999", "Продукты", today.minusMonths(2));

        OperationsPage all = transactionService.list(anna.getId(),
                transactionService.withDefaults(anna.getId(), new TransactionFilter(null, null, null, null, null, null, null)));
        assertThat(all.countText()).isEqualTo("4 операции");
        assertThat(all.expenseText()).isEqualTo("59,20 BYN");
        assertThat(all.incomeText()).isEqualTo("1 900,00 BYN");

        OperationsPage search = transactionService.list(anna.getId(),
                new TransactionFilter(today.minusYears(1), today, null, null, null, "супер", null));
        assertThat(search.countText()).isEqualTo("1 операция");
        assertThat(search.days()).singleElement().satisfies(d -> {
            assertThat(d.expenseText()).isEqualTo("Расходы: 46,80 BYN");
            assertThat(d.items()).extracting("title").containsExactly("Супермаркет");
        });

        Long transport = fixtures.standardCategory("Транспорт").getId();
        OperationsPage byCategory = transactionService.list(anna.getId(),
                new TransactionFilter(today.minusYears(1), today, null, transport, null, null, null));
        assertThat(byCategory.expenseText()).isEqualTo("12,40 BYN");
    }

    @Test
    void listIsPagedByThirty() {
        for (int i = 0; i < 31; i++) {
            fixtures.expense(anna, card, "Кофе " + i, "1", "Кафе и рестораны", today);
        }
        OperationsPage first = transactionService.list(anna.getId(),
                new TransactionFilter(today.minusDays(1), today, null, null, null, null, 0));

        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.days().get(0).items()).hasSize(30);
        assertThat(first.nextUrl()).contains("page=1");
        assertThat(first.expenseText()).isEqualTo("31,00 BYN");
    }
}
