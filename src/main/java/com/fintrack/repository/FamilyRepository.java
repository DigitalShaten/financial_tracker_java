package com.fintrack.repository;

import com.fintrack.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Family f where f.id = :id")
    void deleteFamilyById(@Param("id") Long id);
}
