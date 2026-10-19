package com.fintrack.repository;

import com.fintrack.dto.ContributorTotal;
import com.fintrack.dto.GoalSum;
import com.fintrack.entity.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    /** "Накоплено" по нескольким целям сразу. */
    @Query("""
            select new com.fintrack.dto.GoalSum(c.goal.id, sum(c.amount))
            from GoalContribution c where c.goal.id in :goalIds group by c.goal.id
            """)
    List<GoalSum> sumsByGoals(@Param("goalIds") Collection<Long> goalIds);

    /** Вклад каждого участника в цель. */
    @Query("""
            select new com.fintrack.dto.ContributorTotal(u.id, u.name, sum(c.amount))
            from GoalContribution c join c.user u
            where c.goal.id = :goalId
            group by u.id, u.name
            order by sum(c.amount) desc
            """)
    List<ContributorTotal> contributors(@Param("goalId") Long goalId);

    @Query("""
            select sum(c.amount) from GoalContribution c
            where c.goal.id = :goalId and c.contributedAt between :from and :to
            """)
    BigDecimal sumByGoalBetween(@Param("goalId") Long goalId, @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /** Сколько отложили на цели указанные пользователи за период (для отчёта). */
    @Query("""
            select sum(c.amount) from GoalContribution c
            where c.user.id in :userIds and c.contributedAt between :from and :to
            """)
    BigDecimal sumByUsersBetween(@Param("userIds") Collection<Long> userIds, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /** Сколько внесено в указанные цели за период (для семейного отчёта). */
    @Query("""
            select sum(c.amount) from GoalContribution c
            where c.goal.id in :goalIds and c.contributedAt between :from and :to
            """)
    BigDecimal sumByGoalsBetween(@Param("goalIds") Collection<Long> goalIds, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
