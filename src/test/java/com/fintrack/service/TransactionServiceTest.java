package com.fintrack.service;

import com.fintrack.dto.TransactionForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.event.TransactionCreatedEvent;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private AccessService accessService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher events;

    private TransactionService service;
    private Account card;
    private Account cash;
    private Category food;

    @BeforeEach
    void setUp() {
        // 18 сентября 2026, 12:00 по Минску
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T09:00:00Z"), ZoneOffset.UTC);
        service = new TransactionService(transactionRepository, accountService, categoryService, accessService,
                userRepository, events, clock);

        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        card = account(10L, "100.00");
        cash = account(11L, "20.00");
        when(accountService.getOwned(USER_ID, 10L)).thenReturn(card);
        when(accountService.getOwned(USER_ID, 11L)).thenReturn(cash);

        food = new Category();
        food.setId(5L);
        food.setName("Продукты");
        food.setType(CategoryType.EXPENSE);
        when(categoryService.getAvailable(eq(USER_ID), eq(5L), any())).thenReturn(food);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(99L);
            return tx;
        });
    }

    private static Account account(Long id, String balance) {
        Account a = new Account();
        a.setId(id);
        a.setName("Счёт " + id);
        a.setBalance(new BigDecimal(balance));
        return a;
    }

    private static TransactionForm form(TransactionType type, String amount, Long account, Long target) {
        TransactionForm form = new TransactionForm();
        form.setType(type);
        form.setTitle("Операция");
        form.setAmount(new BigDecimal(amount));
        form.setAccountId(account);
        form.setTargetAccountId(target);
        form.setCategoryId(type == TransactionType.TRANSFER ? null : 5L);
        form.setOperationDate(LocalDate.of(2026, 9, 18));
        return form;
    }

    @Test
    void expenseDecreasesBalance() {
        service.create(USER_ID, form(TransactionType.EXPENSE, "46.80", 10L, null));

        assertThat(card.getBalance()).isEqualByComparingTo("53.20");
    }

    @Test
    void incomeIncreasesBalance() {
        service.create(USER_ID, form(TransactionType.INCOME, "1900", 10L, null));

        assertThat(card.getBalance()).isEqualByComparingTo("2000.00");
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        service.create(USER_ID, form(TransactionType.TRANSFER, "30", 10L, 11L));

        assertThat(card.getBalance()).isEqualByComparingTo("70.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferToSameAccountIsRejected() {
        assertThatThrownBy(() -> service.create(USER_ID, form(TransactionType.TRANSFER, "30", 10L, 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Выберите другой счёт для перевода");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void negativeBalanceIsAllowed() {
        service.create(USER_ID, form(TransactionType.EXPENSE, "150", 10L, null));

        assertThat(card.getBalance()).isEqualByComparingTo("-50.00");
    }

    @Test
    void createPublishesEvent() {
        service.create(USER_ID, form(TransactionType.EXPENSE, "10", 10L, null));

        verify(events).publishEvent(new TransactionCreatedEvent(99L, USER_ID));
    }

    @Test
    void changingAmountAdjustsBalanceByDifference() {
        Transaction tx = service.create(USER_ID, form(TransactionType.EXPENSE, "40", 10L, null));
        when(transactionRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.of(tx));

        service.update(USER_ID, 99L, form(TransactionType.EXPENSE, "25", 10L, null));

        assertThat(card.getBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void changingAccountMovesEffectToNewAccount() {
        Transaction tx = service.create(USER_ID, form(TransactionType.EXPENSE, "10", 10L, null));
        when(transactionRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.of(tx));

        service.update(USER_ID, 99L, form(TransactionType.EXPENSE, "10", 11L, null));

        assertThat(card.getBalance()).isEqualByComparingTo("100.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void deletingReturnsBalance() {
        Transaction tx = service.create(USER_ID, form(TransactionType.TRANSFER, "30", 10L, 11L));
        when(transactionRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.of(tx));

        service.delete(USER_ID, 99L);

        assertThat(card.getBalance()).isEqualByComparingTo("100.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("20.00");
        verify(transactionRepository).delete(tx);
    }

    @Test
    void foreignAccountIsNotFound() {
        when(accountService.getOwned(USER_ID, 77L)).thenThrow(new NotFoundException("Счёт не найден"));

        assertThatThrownBy(() -> service.create(USER_ID, form(TransactionType.EXPENSE, "10", 77L, null)))
                .isInstanceOf(NotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void foreignOperationIsNotFound() {
        when(transactionRepository.findByIdAndUser_Id(anyLong(), eq(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, 500L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void categoryOfWrongTypeIsRejected() {
        when(categoryService.getAvailable(USER_ID, 5L, TransactionType.INCOME))
                .thenThrow(new BusinessException("Категория не подходит к типу операции"));

        assertThatThrownBy(() -> service.create(USER_ID, form(TransactionType.INCOME, "10", 10L, null)))
                .isInstanceOf(BusinessException.class);
        assertThat(card.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void dateAfterTomorrowIsRejected() {
        TransactionForm form = form(TransactionType.EXPENSE, "10", 10L, null);
        form.setOperationDate(LocalDate.of(2026, 9, 20));

        assertThatThrownBy(() -> service.create(USER_ID, form)).isInstanceOf(BusinessException.class);

        form.setOperationDate(LocalDate.of(2026, 9, 19));
        service.create(USER_ID, form);
        assertThat(card.getBalance()).isEqualByComparingTo("90.00");
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRejected() {
        assertThatThrownBy(() -> service.create(USER_ID, form(TransactionType.EXPENSE, "10.005", 10L, null)))
                .isInstanceOf(BusinessException.class);
    }
}
