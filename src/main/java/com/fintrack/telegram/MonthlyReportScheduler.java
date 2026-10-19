package com.fintrack.telegram;

import com.fintrack.entity.NotificationSettings;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.service.MonthlyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Каждый час проверяет, кому пора отправить отчёт за прошлый месяц - по часовому поясу пользователя.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {

    private final NotificationSettingsRepository settingsRepository;
    private final MonthlyReportService reportService;
    private final Clock clock;

    /** Каждый час в 00 минут. */
    @Scheduled(cron = "0 0 * * * *")
    public void sendDueReports() {
        for (NotificationSettings s : settingsRepository.findAllWithMonthlyReportAndTelegram()) {
            ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneId.of(s.getUser().getTimeZone())));
            YearMonth reportMonth = YearMonth.from(now).minusMonths(1);
            boolean dueNow = now.getDayOfMonth() == s.getReportDay() && now.getHour() == s.getReportHour();
            boolean alreadySent = s.getLastReportMonth() != null
                    && YearMonth.from(s.getLastReportMonth()).equals(reportMonth);
            if (dueNow && !alreadySent) {
                try {
                    reportService.sendMonthlyReport(s.getUserId(), reportMonth);
                } catch (RuntimeException e) {
                    log.error("Отчёт для пользователя {} не отправлен", s.getUserId(), e);
                }
            }
        }
    }
}
