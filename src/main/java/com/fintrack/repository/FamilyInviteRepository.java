package com.fintrack.repository;

import com.fintrack.entity.FamilyInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FamilyInviteRepository extends JpaRepository<FamilyInvite, Long> {

    Optional<FamilyInvite> findByCode(String code);

    boolean existsByCode(String code);

    /** Последний действующий (не использованный и не истёкший) код семьи. */
    Optional<FamilyInvite> findFirstByFamily_IdAndUsedAtIsNullAndExpiresAtAfterOrderByExpiresAtDesc(
            Long familyId, OffsetDateTime now);

    /** Гасит все действующие коды семьи: срок действия заканчивается прямо сейчас. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FamilyInvite i set i.expiresAt = :now
            where i.family.id = :familyId and i.usedAt is null and i.expiresAt > :now
            """)
    int expireActive(@Param("familyId") Long familyId, @Param("now") OffsetDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FamilyInvite i where i.family.id = :familyId")
    void deleteAllByFamilyId(@Param("familyId") Long familyId);
}
