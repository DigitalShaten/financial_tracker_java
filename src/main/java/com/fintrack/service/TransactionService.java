package com.fintrack.service;

import com.fintrack.dto.*;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.event.TransactionCreatedEvent;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.TransactionSpecs;
import com.fintrack.repository.UserRepository;
import com.fintrack.util.DateFormat;
import com.fintrack.util.MoneyFormat;
import com.fintrack.util.Plurals;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

/**
 * Операции и балансы счетов. Создание, изменение и удаление операции вместе с изменением
 * балансов выполняются в одной транзакции: либо всё, либо ничего.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    public static final int PAGE_SIZE = 30;

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final AccessService accessService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public Transaction create(Long userId, TransactionForm form) {
        Transaction tx = new Transaction();
        tx.setUser(userRepository.getReferenceById(userId));
        fill(userId, tx, form);
        apply(tx);
        Transaction saved = transactionRepository.save(tx);
        events.publishEvent(new TransactionCreatedEvent(saved.getId(), userId));
        return saved;
    }

    /** Изменение: отменить старое влияние на балансы -> заполнить новыми данными -> применить. */
    @Transactional
    public void update(Long userId, Long txId, TransactionForm form) {
        Transaction tx = getOwned(userId, txId);
        revert(tx);
        fill(userId, tx, form);
        apply(tx);
    }

    @Transactional
    public void delete(Long userId, Long txId) {
        Transaction tx = getOwned(userId, txId);
        revert(tx);
        transactionRepository.delete(tx);
    }

    /** Своя операция; чужая или несуществующая -> 404. */
    @Transactional(readOnly = true)
    public Transaction getOwned(Long userId, Long txId) {
        return transactionRepository.findByIdAndUser_Id(txId, userId)
                .orElseThrow(() -> new NotFoundException("Операция не найдена"));
    }

    /** Данные своей операции для формы изменения. */
    @Transactional(readOnly = true)
    public TransactionForm formOf(Long userId, Long txId) {
        Transaction tx = getOwned(userId, txId);
        TransactionForm form = new TransactionForm();
        form.setType(tx.getType());
        form.setTitle(tx.getTitle());
        form.setAmount(tx.getAmount());
        form.setAccountId(tx.getAccount().getId());
        form.setTargetAccountId(tx.getTargetAccount() == null ? null : tx.getTargetAccount().getId());
        form.setCategoryId(tx.getCategory() == null ? null : tx.getCategory().getId());
        form.setOperationDate(tx.getOperationDate());
        form.setComment(tx.getComment());
        return form;
    }

    private void fill(Long userId, Transaction tx, TransactionForm form) {
        BigDecimal amount = form.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Сумма должна быть больше нуля");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("В сумме не больше двух знаков после запятой");
        }
        LocalDate tomorrow = today(userId).plusDays(1);
        if (form.getOperationDate() == null || form.getOperationDate().isAfter(tomorrow)) {
            throw new BusinessException("Дата операции — не позже завтрашнего дня");
        }
        tx.setType(form.getType());
        tx.setTitle(form.getTitle().trim());
        tx.setAmount(amount);
        tx.setOperationDate(form.getOperationDate());
        tx.setComment(form.getComment() == null || form.getComment().isBlank() ? null : form.getComment().trim());
        tx.setAccount(accountService.getOwned(userId, form.getAccountId()));

        if (form.getType() == TransactionType.TRANSFER) {
            if (form.getTargetAccountId() == null || form.getTargetAccountId().equals(form.getAccountId())) {
                throw new BusinessException("Выберите другой счёт для перевода");
            }
            tx.setTargetAccount(accountService.getOwned(userId, form.getTargetAccountId()));
            tx.setCategory(null);
        } else {
            tx.setTargetAccount(null);
            tx.setCategory(categoryService.getAvailable(userId, form.getCategoryId(), form.getType()));
        }
    }

    /** Применяет операцию к балансам счетов. */
    private void apply(Transaction tx) {
        switch (tx.getType()) {
            case INCOME -> tx.getAccount().deposit(tx.getAmount());
            case EXPENSE -> tx.getAccount().withdraw(tx.getAmount());
            case TRANSFER -> {
                tx.getAccount().withdraw(tx.getAmount());
                tx.getTargetAccount().deposit(tx.getAmount());
            }
        }
    }

    /** Отменяет влияние операции на балансы - зеркально {@link #apply}. */
    private void revert(Transaction tx) {
        switch (tx.getType()) {
            case INCOME -> tx.getAccount().withdraw(tx.getAmount());
            case EXPENSE -> tx.getAccount().deposit(tx.getAmount());
            case TRANSFER -> {
                tx.getAccount().deposit(tx.getAmount());
                tx.getTargetAccount().withdraw(tx.getAmount());
            }
        }
    }

    /** Сегодняшняя дата в часовом поясе пользователя. */
    @Transactional(readOnly = true)
    public LocalDate today(Long userId) {
        String zone = userRepository.findById(userId).map(User::getTimeZone).orElse("Europe/Minsk");
        return LocalDate.now(clock.withZone(ZoneId.of(zone)));
    }

    /** Фильтр с периодом по умолчанию - текущий месяц. */
    public TransactionFilter withDefaults(Long userId, TransactionFilter filter) {
        if (filter.from() != null || filter.to() != null) {
            return filter;
        }
        YearMonth month = YearMonth.from(today(userId));
        return new TransactionFilter(month.atDay(1), month.atEndOfMonth(), filter.accountId(),
                filter.categoryId(), filter.memberId(), filter.query(), filter.page());
    }

    /**
     * Список операций по фильтру: свои счета и открытые счета семьи, по 30 на страницу,
     * сгруппированный по дням, с итогами "Расходы / Доходы" по тем же фильтрам.
     */
    @Transactional(readOnly = true)
    public OperationsPage list(Long userId, TransactionFilter filter) {
        Set<Long> visible = accessService.visibleAccountIds(userId, ViewMode.all());
        // Скрытый или чужой счёт и участник не из своей семьи - "не найдено", а не пустой список
        if (filter.accountId() != null && !visible.contains(filter.accountId())) {
            throw new NotFoundException("Счёт не найден");
        }
        if (filter.memberId() != null && !accessService.sameFamily(userId, filter.memberId())) {
            throw new NotFoundException("Участник не найден");
        }
        Specification<Transaction> spec = Specification.allOf(
                TransactionSpecs.onAccounts(visible),
                TransactionSpecs.between(filter.from(), filter.to()),
                TransactionSpecs.account(filter.accountId()),
                TransactionSpecs.category(filter.categoryId()),
                TransactionSpecs.member(filter.memberId()),
                TransactionSpecs.search(filter.query()));
        Pageable pageable = PageRequest.of(filter.pageIndex(), PAGE_SIZE,
                Sort.by(Sort.Order.desc("operationDate"), Sort.Order.desc("id")));
        Page<Transaction> page = transactionRepository.findAll(spec, pageable);

        String currency = currency(userId);
        Map<TransactionType, BigDecimal> totals = transactionRepository.totalsByType(spec);

        Map<LocalDate, List<OperationView>> byDay = new LinkedHashMap<>();
        for (Transaction tx : page.getContent()) {
            byDay.computeIfAbsent(tx.getOperationDate(), d -> new ArrayList<>())
                    .add(toView(tx, userId, visible, currency));
        }
        Map<LocalDate, BigDecimal> dayExpenses = byDay.isEmpty() ? Map.of()
                : transactionRepository.expensesByDay(spec.and(TransactionSpecs.onDates(byDay.keySet())));
        List<DayGroup> days = byDay.entrySet().stream()
                .map(e -> {
                    BigDecimal spent = dayExpenses.get(e.getKey());
                    return new DayGroup(DateFormat.dayMonthWeekday(e.getKey()),
                            spent == null ? null : "Расходы: " + MoneyFormat.format(spent, currency), e.getValue());
                })
                .toList();

        long count = page.getTotalElements();
        return new OperationsPage(days,
                Plurals.count(count, "операция", "операции", "операций"),
                MoneyFormat.format(totals.getOrDefault(TransactionType.EXPENSE, BigDecimal.ZERO), currency),
                MoneyFormat.format(totals.getOrDefault(TransactionType.INCOME, BigDecimal.ZERO), currency),
                page.getNumber() + 1, Math.max(page.getTotalPages(), 1),
                page.hasPrevious() ? pageUrl(filter, page.getNumber() - 1) : null,
                page.hasNext() ? pageUrl(filter, page.getNumber() + 1) : null);
    }

    /** Последние операции на заданных счетах (для обзора) - не позже указанной даты. */
    @Transactional(readOnly = true)
    public List<OperationView> recent(Long userId, Set<Long> accountIds, LocalDate until, int limit) {
        Specification<Transaction> spec = Specification.allOf(
                TransactionSpecs.onAccounts(accountIds), TransactionSpecs.between(null, until));
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Order.desc("operationDate"), Sort.Order.desc("id")));
        Set<Long> visible = accessService.visibleAccountIds(userId, ViewMode.all());
        String currency = currency(userId);
        return transactionRepository.findAll(spec, pageable).getContent().stream()
                .map(tx -> toView(tx, userId, visible, currency))
                .toList();
    }

    private String currency(Long userId) {
        return userRepository.findById(userId).map(User::getCurrency).orElse("BYN");
    }

    private static String pageUrl(TransactionFilter f, int page) {
        return UriComponentsBuilder.fromPath("/operations")
                .queryParamIfPresent("from", Optional.ofNullable(f.from()))
                .queryParamIfPresent("to", Optional.ofNullable(f.to()))
                .queryParamIfPresent("accountId", Optional.ofNullable(f.accountId()))
                .queryParamIfPresent("categoryId", Optional.ofNullable(f.categoryId()))
                .queryParamIfPresent("memberId", Optional.ofNullable(f.memberId()))
                .queryParamIfPresent("query", Optional.ofNullable(f.query()).filter(q -> !q.isBlank()))
                .queryParam("page", page)
                .encode().build().toUriString();
    }

    static OperationView toView(Transaction tx, Long viewerId, Set<Long> visibleAccounts, String currency) {
        User author = tx.getUser();
        boolean mine = author.getId().equals(viewerId);
        String accountName = tx.getAccount().getName();
        String category;
        String sign;
        if (tx.getType() == TransactionType.TRANSFER) {
            String target = visibleAccounts.contains(tx.getTargetAccount().getId()) || mine
                    ? tx.getTargetAccount().getName() : "скрытый счёт";
            accountName = accountName + " → " + target;
            category = "Перевод";
            sign = "";
        } else {
            category = tx.getCategory().getName();
            sign = tx.getType() == TransactionType.INCOME ? "+ " : "− ";
        }
        return new OperationView(tx.getId(), tx.getTitle(), tx.getComment(), accountName, author.getName(),
                CurrentUserView.initialOf(author.getName()), mine, category, tx.getType(),
                sign + MoneyFormat.format(tx.getAmount(), currency), shortDate(tx.getOperationDate()));
    }

    private static String shortDate(LocalDate date) {
        return DateFormat.dayMonth(date);
    }
}
