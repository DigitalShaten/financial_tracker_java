package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface TransactionTotalsRepository {

    Map<TransactionType, BigDecimal> totalsByType(Specification<Transaction> spec);

    Map<LocalDate, BigDecimal> expensesByDay(Specification<Transaction> spec);
}
