package com.fintrack.repository;

import com.fintrack.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findAllByOwner_IdAndFamilyIsNullOrderByCreatedAtAscIdAsc(Long ownerId);

    List<Goal> findAllByFamily_IdOrderByCreatedAtAscIdAsc(Long familyId);

    /** Семейные цели становятся личными целями их создателей. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Goal g set g.family = null where g.family.id = :familyId")
    void detachAllFromFamily(@Param("familyId") Long familyId);

    /** Семейные цели, созданные пользователем, становятся его личными (при выходе из семьи). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Goal g set g.family = null where g.family.id = :familyId and g.owner.id = :ownerId")
    void detachOwnerGoalsFromFamily(@Param("familyId") Long familyId, @Param("ownerId") Long ownerId);
}
