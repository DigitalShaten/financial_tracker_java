package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Условия фильтра операций. Пустой параметр — «без ограничения» (cb.conjunction()).
 */
public final class TransactionSpecs {

    private TransactionSpecs() {
    }

    /** Только операции на видимых счетах. Пустое множество — ни одной операции. */
    public static Specification<Transaction> onAccounts(Collection<Long> accountIds) {
        return (root, query, cb) -> accountIds.isEmpty()
                ? cb.disjunction()
                : root.get("account").get("id").in(accountIds);
    }

    public static Specification<Transaction> between(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) {
                return cb.conjunction();
            }
            if (from == null) {
                return cb.lessThanOrEqualTo(root.get("operationDate"), to);
            }
            if (to == null) {
                return cb.greaterThanOrEqualTo(root.get("operationDate"), from);
            }
            return cb.between(root.get("operationDate"), from, to);
        };
    }

    public static Specification<Transaction> account(Long accountId) {
        return (root, query, cb) -> accountId == null ? cb.conjunction()
                : cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<Transaction> category(Long categoryId) {
        return (root, query, cb) -> categoryId == null ? cb.conjunction()
                : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> member(Long userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction()
                : cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Transaction> search(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + text.trim().toLowerCase() + "%";
            return cb.or(cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("comment")), like));
        };
    }

    public static Specification<Transaction> onDates(Collection<LocalDate> dates) {
        return (root, query, cb) -> dates.isEmpty() ? cb.disjunction() : root.get("operationDate").in(dates);
    }
}
