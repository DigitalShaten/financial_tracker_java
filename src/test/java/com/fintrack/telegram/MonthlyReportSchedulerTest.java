package com.fintrack.telegram;

import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.User;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.service.MonthlyReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyReportSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T07:00:00Z"), ZoneOffset.UTC);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    private NotificationSettingsRepository settingsRepository;
    private MonthlyReportService reportService;
    private MonthlyReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(NotificationSettingsRepository.class);
        reportService = mock(MonthlyReportService.class);
        scheduler = new MonthlyReportScheduler(settingsRepository, reportService, CLOCK);
    }

    @Test
    void sendsAtReportHourInUsersTimeZone() {
        NotificationSettings minsk = settings(1L, "Europe/Minsk", 1, 10, null);
        NotificationSettings tokyo = settings(2L, "Asia/Tokyo", 1, 10, null);
        when(settingsRepository.findAllWithMonthlyReportAndTelegram()).thenReturn(List.of(minsk, tokyo));

        scheduler.sendDueReports();

        verify(reportService).sendMonthlyReport(1L, SEPTEMBER);
        verify(reportService, never()).sendMonthlyReport(2L, SEPTEMBER);
    }

    @Test
    void wrongDayIsSkipped() {
        when(settingsRepository.findAllWithMonthlyReportAndTelegram())
                .thenReturn(List.of(settings(1L, "Europe/Minsk", 2, 10, null)));

        scheduler.sendDueReports();

        verify(reportService, never()).sendMonthlyReport(anyLong(), any());
    }

    @Test
    void doesNotResendSameMonth() {
        when(settingsRepository.findAllWithMonthlyReportAndTelegram())
                .thenReturn(List.of(settings(1L, "Europe/Minsk", 1, 10, LocalDate.of(2026, 9, 1))));

        scheduler.sendDueReports();

        verify(reportService, never()).sendMonthlyReport(anyLong(), any());
    }

    @Test
    void errorForOneUserDoesNotStopOthers() {
        when(settingsRepository.findAllWithMonthlyReportAndTelegram()).thenReturn(List.of(
                settings(1L, "Europe/Minsk", 1, 10, null), settings(3L, "Europe/Moscow", 1, 10, null)));
        doThrow(new IllegalStateException("сбой")).when(reportService).sendMonthlyReport(1L, SEPTEMBER);

        scheduler.sendDueReports();

        verify(reportService).sendMonthlyReport(3L, SEPTEMBER);
    }

    private static NotificationSettings settings(Long userId, String zone, int day, int hour, LocalDate lastMonth) {
        User user = new User();
        user.setId(userId);
        user.setTimeZone(zone);
        NotificationSettings s = new NotificationSettings();
        s.setUserId(userId);
        s.setUser(user);
        s.setReportDay((short) day);
        s.setReportHour((short) hour);
        s.setLastReportMonth(lastMonth);
        return s;
    }
}
