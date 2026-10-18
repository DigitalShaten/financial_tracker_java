package com.fintrack.repository;

import com.fintrack.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByUser_IdAndCategory_IdAndMonth(Long userId, Long categoryId, LocalDate month);

    @Query("""
            select b from Budget b join fetch b.category
            where b.user.id in :userIds and b.month = :month
            """)
    List<Budget> findAllWithCategories(@Param("userIds") Collection<Long> userIds,
                                       @Param("month") LocalDate month);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Budget b where b.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
