package com.fintrack.repository;

import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Стандартные и свои неархивные категории пользователя. */
    @Query("""
            select c from Category c left join c.user u
            where (u is null or u.id = :userId) and c.archived = false
            order by c.type, c.name
            """)
    List<Category> findAvailable(@Param("userId") Long userId);

    /** Категория, доступная пользователю: стандартная или его собственная. */
    @Query("""
            select c from Category c left join c.user u
            where c.id = :id and (u is null or u.id = :userId)
            """)
    Optional<Category> findAvailableById(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Category> findByIdAndUser_Id(Long id, Long userId);

    Optional<Category> findByIdAndUserIsNull(Long id);

    List<Category> findAllByUser_IdOrderByTypeAscNameAsc(Long userId);

    List<Category> findAllByUserIsNullOrderByTypeAscNameAsc();

    long countByUserIsNull();

    @Query("""
            select count(c) > 0 from Category c left join c.user u
            where lower(c.name) = lower(:name) and c.type = :type and c.archived = false
              and (u is null or u.id = :userId)
            """)
    boolean existsAvailableByName(@Param("userId") Long userId, @Param("name") String name,
                                  @Param("type") CategoryType type);

    boolean existsByUserIsNullAndTypeAndNameIgnoreCase(CategoryType type, String name);
}
