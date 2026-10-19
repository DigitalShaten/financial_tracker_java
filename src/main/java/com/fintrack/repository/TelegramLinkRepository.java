package com.fintrack.repository;

import com.fintrack.entity.TelegramLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, Long> {

    Optional<TelegramLink> findByUser_Id(Long userId);

    Optional<TelegramLink> findByChatId(Long chatId);

    Optional<TelegramLink> findByLinkCode(String linkCode);

    List<TelegramLink> findAllByUser_IdIn(Collection<Long> userIds);
}
