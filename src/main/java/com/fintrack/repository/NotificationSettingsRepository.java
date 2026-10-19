package com.fintrack.repository;

import com.fintrack.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    /** Настройки пользователей, у которых включён ежемесячный отчёт и привязан Telegram-чат. */
    @Query("""
            select s from NotificationSettings s join fetch s.user u
            where s.monthlyReport = true
              and exists (select l.id from TelegramLink l where l.user = u and l.chatId is not null)
            """)
    List<NotificationSettings> findAllWithMonthlyReportAndTelegram();
}
