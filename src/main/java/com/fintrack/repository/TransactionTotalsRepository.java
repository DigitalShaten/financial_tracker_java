package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Суммы по тем же фильтрам, что и список операций. Считает база данных (SUM ... GROUP BY),
 * а не Java: все операции в память не загружаются.
 */
public interface TransactionTotalsRepository {

    Map<TransactionType, BigDecimal> totalsByType(Specification<Transaction> spec);

    Map<LocalDate, BigDecimal> expensesByDay(Specification<Transaction> spec);
}
