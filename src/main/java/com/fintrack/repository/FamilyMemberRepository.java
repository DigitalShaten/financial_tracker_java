package com.fintrack.repository;

import com.fintrack.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    Optional<FamilyMember> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    /** Id всех остальных участников семьи пользователя (без него самого). */
    @Query("""
            select other.user.id from FamilyMember me, FamilyMember other
            where me.user.id = :userId and other.family = me.family and other.user.id <> :userId
            """)
    List<Long> findFamilyUserIds(@Param("userId") Long userId);

    /** Участники семьи вместе с пользователями, в порядке вступления. */
    @Query("""
            select m from FamilyMember m join fetch m.user
            where m.family.id = :familyId
            order by m.joinedAt, m.id
            """)
    List<FamilyMember> findAllWithUsers(@Param("familyId") Long familyId);

    long countByFamily_Id(Long familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FamilyMember m where m.family.id = :familyId")
    void deleteAllByFamilyId(@Param("familyId") Long familyId);
}
