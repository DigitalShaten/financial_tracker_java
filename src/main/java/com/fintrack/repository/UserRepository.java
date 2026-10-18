package com.fintrack.repository;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRoleAndEmailContainingIgnoreCase(Role role, String email, Pageable pageable);

    List<User> findAllByRoleOrderByEmail(Role role);

    long countByRole(Role role);

    /** Удаление одним SQL-запросом: связанные строки удаляет база (ON DELETE CASCADE). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from User u where u.id = :id")
    void deleteUserById(@Param("id") Long id);
}
