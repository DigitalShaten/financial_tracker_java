package com.fintrack.repository;

import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    List<Account> findByEmailIgnoreCase(String email);

    List<Account> existsByEmailIgnoreCase(String email);
}
