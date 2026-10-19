package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class TransactionTotalsRepositoryImpl implements TransactionTotalsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Map<TransactionType, BigDecimal> totalsByType(Specification<Transaction> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);
        Expression<TransactionType> type = root.get("type");
        Expression<BigDecimal> sum = cb.sum(root.<BigDecimal>get("amount"));
        query.multiselect(type, sum).where(predicate(spec, root, query, cb)).groupBy(type);

        Map<TransactionType, BigDecimal> result = new EnumMap<>(TransactionType.class);
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            result.put(row.get(0, TransactionType.class), row.get(1, BigDecimal.class));
        }
        return result;
    }

    @Override
    public Map<LocalDate, BigDecimal> expensesByDay(Specification<Transaction> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);
        Expression<LocalDate> date = root.get("operationDate");
        Expression<BigDecimal> sum = cb.sum(root.<BigDecimal>get("amount"));
        Predicate expense = cb.equal(root.get("type"), TransactionType.EXPENSE);
        query.multiselect(date, sum).where(cb.and(predicate(spec, root, query, cb), expense)).groupBy(date);

        Map<LocalDate, BigDecimal> result = new HashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            result.put(row.get(0, LocalDate.class), row.get(1, BigDecimal.class));
        }
        return result;
    }

    private static Predicate predicate(Specification<Transaction> spec, Root<Transaction> root,
                                       CriteriaQuery<?> query, CriteriaBuilder cb) {
        Predicate predicate = spec.toPredicate(root, query, cb);
        return predicate == null ? cb.conjunction() : predicate;
    }
}
