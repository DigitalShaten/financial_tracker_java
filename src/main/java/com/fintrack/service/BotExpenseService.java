package com.fintrack.service;

import com.fintrack.dto.CategoryView;
import com.fintrack.dto.TransactionForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.Category;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.exception.BusinessException;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.util.MoneyFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Запись расхода из Telegram. Сама операция создаётся обычным {@link TransactionService},
 * поэтому все проверки и уведомление о бюджете работают так же, как на сайте.
 */
@Service
@RequiredArgsConstructor
public class BotExpenseService {

    private static final int TITLE_MAX = 120;

    private final CategoryService categoryService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<CategoryView> categories(Long userId) {
        return categoryService.availableExpenses(userId);
    }

    /** Категория расходов, доступная пользователю (своя или стандартная), иначе 404. */
    @Transactional(readOnly = true)
    public Category category(Long userId, Long categoryId) {
        return categoryService.getAvailable(userId, categoryId, TransactionType.EXPENSE);
    }

    /**
     * Записывает расход на сегодня. Счёт - тот же, что в последнем расходе, а если его нет
     * или он в архиве - первый активный счёт. Возвращает текст для ответа в чат.
     */
    @Transactional
    public String addExpense(Long userId, Long categoryId, BigDecimal amount, String comment) {
        Category category = category(userId, categoryId);
        Account account = accountFor(userId);

        String note = comment == null ? "" : comment.trim();
        TransactionForm form = new TransactionForm();
        form.setType(TransactionType.EXPENSE);
        form.setAmount(amount);
        form.setAccountId(account.getId());
        form.setCategoryId(category.getId());
        form.setOperationDate(transactionService.today(userId));
        if (note.isEmpty()) {
            form.setTitle(category.getName());
        } else if (note.length() <= TITLE_MAX) {
            form.setTitle(note);
        } else {
            // длинный текст целиком уходит в комментарий, в названии - начало
            form.setTitle(note.substring(0, TITLE_MAX - 3) + "...");
            form.setComment(note.length() > 500 ? note.substring(0, 500) : note);
        }
        transactionService.create(userId, form);

        String currency = userService.get(userId).getCurrency();
        return "Записано: " + category.getName() + " − " + MoneyFormat.format(amount, currency)
                + (note.isEmpty() ? "" : " («" + form.getTitle() + "»)")
                + "\nСчёт «" + account.getName() + "», остаток " + MoneyFormat.format(account.getBalance(), currency);
    }

    private Account accountFor(Long userId) {
        List<Account> active = accountService.activeAccounts(userId);
        if (active.isEmpty()) {
            throw new BusinessException("Нет ни одного активного счёта. Добавьте счёт на сайте.");
        }
        return transactionRepository.findFirstByUser_IdAndTypeOrderByIdDesc(userId, TransactionType.EXPENSE)
                .map(Transaction::getAccount)
                .filter(a -> !a.isArchived())
                .orElse(active.get(0));
    }
}
