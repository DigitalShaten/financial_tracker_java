package com.fintrack.repository;

import com.fintrack.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByUser_IdAndArchivedFalseOrderByName(Long userId);

    /** Все счета пользователя: сначала активные, затем архивные. */
    List<Account> findAllByUser_IdOrderByArchivedAscNameAsc(Long userId);

    List<Account> findAllByUser_IdInAndSharedWithFamilyTrueAndArchivedFalse(Collection<Long> userIds);

    Optional<Account> findByIdAndUser_Id(Long id, Long userId);

    @Query("select a from Account a join fetch a.user where a.id in :ids order by a.name")
    List<Account> findAllWithUsers(@Param("ids") Collection<Long> ids);

    @Query("select sum(a.balance) from Account a where a.id in :ids")
    BigDecimal sumBalance(@Param("ids") Collection<Long> ids);

    /** При смене валюты в профиле валюта счетов меняется вместе с ней (без конвертации, см. README). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Account a set a.currency = :currency where a.user.id = :userId")
    void updateCurrency(@Param("userId") Long userId, @Param("currency") String currency);
}
