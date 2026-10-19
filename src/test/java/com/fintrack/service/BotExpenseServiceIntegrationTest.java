package com.fintrack.service;

import com.fintrack.dto.CategoryForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BotExpenseServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private BotExpenseService expenseService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryService categoryService;

    private User dmitry;
    private Long groceries;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
        groceries = fixtures.standardCategory("Продукты").getId();
    }

    @Test
    void expenseIsSavedTodayAndBalanceDecreases() {
        Account cash = accountService.activeAccounts(dmitry.getId()).get(0);   // "Наличные", 0

        String reply = expenseService.addExpense(dmitry.getId(), groceries, new BigDecimal("46.80"), "Супермаркет");

        Transaction tx = transactionRepository.findFirstByUser_IdAndTypeOrderByIdDesc(dmitry.getId(),
                TransactionType.EXPENSE).orElseThrow();
        assertThat(tx.getTitle()).isEqualTo("Супермаркет");
        assertThat(tx.getOperationDate()).isEqualTo(transactionService.today(dmitry.getId()));
        assertThat(accountRepository.findById(cash.getId()).orElseThrow().getBalance()).isEqualByComparingTo("-46.80");
        assertThat(reply).startsWith("Записано: Продукты").contains("Наличные");
    }

    @Test
    void withoutCommentTitleIsCategoryName() {
        expenseService.addExpense(dmitry.getId(), groceries, new BigDecimal("10"), null);

        assertThat(transactionRepository.findFirstByUser_IdAndTypeOrderByIdDesc(dmitry.getId(),
                TransactionType.EXPENSE).orElseThrow().getTitle()).isEqualTo("Продукты");
    }

    @Test
    void usesAccountOfLastExpense() {
        Account card = fixtures.createAccount(dmitry, "Зарплатная карта", "500", true);
        fixtures.expense(dmitry, card, "Такси", "12", "Транспорт", transactionService.today(dmitry.getId()));

        String reply = expenseService.addExpense(dmitry.getId(), groceries, new BigDecimal("20"), "Хлеб");

        assertThat(reply).contains("Зарплатная карта");
        assertThat(accountRepository.findById(card.getId()).orElseThrow().getBalance()).isEqualByComparingTo("468");
    }

    @Test
    void otherUsersCategoryIsNotFound() {
        User anna = fixtures.createUser("Анна", "anna@test.by");
        CategoryForm form = new CategoryForm();
        form.setName("Хобби Анны");
        Long annaCategory = categoryService.create(anna.getId(), form).getId();

        assertThatThrownBy(() -> expenseService.addExpense(dmitry.getId(), annaCategory, BigDecimal.TEN, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void incomeCategoryIsRejected() {
        Long salary = fixtures.standardCategory("Зарплата").getId();

        assertThatThrownBy(() -> expenseService.addExpense(dmitry.getId(), salary, BigDecimal.TEN, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void noActiveAccountGivesClearMessage() {
        Account cash = accountService.activeAccounts(dmitry.getId()).get(0);
        accountService.toggleArchive(dmitry.getId(), cash.getId());

        assertThatThrownBy(() -> expenseService.addExpense(dmitry.getId(), groceries, BigDecimal.TEN, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Нет ни одного активного счёта");
    }
}
