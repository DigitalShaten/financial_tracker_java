package com.fintrack.repository;

import com.fintrack.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {


    @Query("""
            select c from Category c
            where (c.user is null or c.user.id = :userId) and c.archived = false
            order by c.type, c.name
            """)
    List<Category> findAvailable(@Param("userId") Long userId);
}