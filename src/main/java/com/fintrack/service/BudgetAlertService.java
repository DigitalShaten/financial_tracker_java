package com.fintrack.service;

import com.fintrack.entity.Budget;
import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.event.TransactionCreatedEvent;
import com.fintrack.repository.BudgetRepository;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.telegram.TelegramSender;
import com.fintrack.util.DateFormat;
import com.fintrack.util.MoneyFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Уведомление о бюджете: после коммита новой операции (в отдельном потоке) проверяет, пересекла ли
 * она лимит категории - "потрачено до < лимит <= потрачено после". Только в этот момент приходит сообщение.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetAlertService {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final TelegramLinkService linkService;
    private final UserService userService;
    private final TelegramSender sender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTransactionCreated(TransactionCreatedEvent event) {
        try {
            check(event.transactionId(), event.userId());
        } catch (RuntimeException e) {
            log.warn("Не удалось проверить бюджет для операции {}", event.transactionId(), e);
        }
    }

    void check(Long transactionId, Long userId) {
        Optional<Transaction> found = transactionRepository.findById(transactionId);
        if (found.isEmpty() || found.get().getType() != TransactionType.EXPENSE || found.get().getCategory() == null) {
            return;
        }
        Transaction tx = found.get();
        boolean alertsOn = settingsRepository.findById(userId).map(NotificationSettings::isBudgetAlerts).orElse(false);
        Long chatId = linkService.chatOf(userId).orElse(null);
        if (!alertsOn || chatId == null) {
            return;
        }
        YearMonth month = YearMonth.from(tx.getOperationDate());
        Long categoryId = tx.getCategory().getId();
        Optional<Budget> budget = budgetRepository.findByUser_IdAndCategory_IdAndMonth(userId, categoryId, month.atDay(1));
        if (budget.isEmpty()) {
            return;
        }
        BigDecimal limit = budget.get().getLimitAmount();
        BigDecimal after = Optional.ofNullable(transactionRepository.sumUserExpenses(userId, categoryId,
                month.atDay(1), month.atEndOfMonth())).orElse(BigDecimal.ZERO);
        BigDecimal before = after.subtract(tx.getAmount());
        if (before.compareTo(limit) < 0 && limit.compareTo(after) <= 0) {
            String currency = userService.get(userId).getCurrency();
            String state = after.compareTo(limit) > 0 ? "превышен" : "исчерпан";
            sender.send(chatId, "Бюджет «" + tx.getCategory().getName() + "» на " + DateFormat.monthName(month.getMonth())
                    + " " + state + ": потрачено " + MoneyFormat.format(after, currency) + " из "
                    + MoneyFormat.format(limit, currency) + ".");
        }
    }
}
