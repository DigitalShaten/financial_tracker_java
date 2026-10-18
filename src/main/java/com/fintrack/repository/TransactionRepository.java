package com.fintrack.repository;

import com.fintrack.dto.CategoryTotal;
import com.fintrack.dto.MonthAuthorTotal;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction>, TransactionTotalsRepository {

    /** Список с фильтрами; счёт, категория и автор загружаются сразу (без N+1 запросов). */
    @Override
    @EntityGraph(attributePaths = {"account", "targetAccount", "category", "user"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    Optional<Transaction> findByIdAndUser_Id(Long id, Long userId);

    @Query("""
            select count(t) > 0 from Transaction t
            where t.account.id = :accountId or t.targetAccount.id = :accountId
            """)
    boolean existsByAnyAccount(@Param("accountId") Long accountId);

    boolean existsByCategory_Id(Long categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Transaction t where t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    /** Расходы по категориям на заданных счетах за период. */
    @Query("""
            select new com.fintrack.dto.CategoryTotal(c.id, c.name, sum(t.amount))
            from Transaction t join t.category c
            where t.account.id in :accountIds
              and t.type = com.fintrack.entity.TransactionType.EXPENSE
              and t.operationDate between :from and :to
            group by c.id, c.name
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> expensesByCategory(@Param("accountIds") Collection<Long> accountIds,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /** Расходы по месяцам и авторам — для графика на обзоре. */
    @Query("""
            select new com.fintrack.dto.MonthAuthorTotal(
                       extract(year from t.operationDate), extract(month from t.operationDate),
                       t.user.id, sum(t.amount))
            from Transaction t
            where t.account.id in :accountIds
              and t.type = com.fintrack.entity.TransactionType.EXPENSE
              and t.operationDate between :from and :to
            group by extract(year from t.operationDate), extract(month from t.operationDate), t.user.id
            """)
    List<MonthAuthorTotal> expensesByMonthAndAuthor(@Param("accountIds") Collection<Long> accountIds,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /** Сумма операций одного типа; null, если операций нет (используйте {@link #sumByTypeOrZero}). */
    @Query("""
            select sum(t.amount) from Transaction t
            where t.account.id in :accountIds and t.type = :type
              and t.operationDate between :from and :to
            """)
    BigDecimal sumByType(@Param("accountIds") Collection<Long> accountIds,
                         @Param("type") TransactionType type,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    default BigDecimal sumByTypeOrZero(Collection<Long> accountIds, TransactionType type, LocalDate from, LocalDate to) {
        if (accountIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = sumByType(accountIds, type, from, to);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /** Расходы пользователя по категории за период (все его счета) — для уведомления о бюджете. */
    @Query("""
            select sum(t.amount) from Transaction t
            where t.user.id = :userId and t.category.id = :categoryId
              and t.type = com.fintrack.entity.TransactionType.EXPENSE
              and t.operationDate between :from and :to
            """)
    BigDecimal sumUserExpenses(@Param("userId") Long userId, @Param("categoryId") Long categoryId,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);
}
