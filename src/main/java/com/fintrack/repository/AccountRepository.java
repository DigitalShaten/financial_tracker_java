package com.fintrack.repository;

import com.fintrack.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByUser_IdAndArchivedFalseOrderByName(Long userId);

    Optional<Account> findByIdAndUser_Id(Long id, Long userId);

    List<Account> findAllByUser_IdInAndSharedWithFamilyTrueAndArchivedFalse(Collection<Long> userIds);
}
