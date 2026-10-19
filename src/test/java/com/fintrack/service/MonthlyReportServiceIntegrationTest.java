package com.fintrack.service;

import com.fintrack.entity.Account;
import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.User;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.support.IntegrationTest;
import com.fintrack.telegram.MonthlyReportScheduler;
import com.fintrack.util.DateFormat;
import com.fintrack.util.MoneyFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyReportServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private MonthlyReportService reportService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private NotificationSettingsRepository settingsRepository;

    @Autowired
    private UserRepository userRepository;

    private User dmitry;
    private Account card;
    private YearMonth lastMonth;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
        card = fixtures.createAccount(dmitry, "Зарплатная карта", "1000", true);
        lastMonth = YearMonth.from(transactionService.today(dmitry.getId())).minusMonths(1);
        LocalDate day = lastMonth.atDay(10);
        fixtures.income(dmitry, card, "Зарплата", "2400", day);
        fixtures.expense(dmitry, card, "Супермаркет", "498.10", "Продукты", day);
        fixtures.expense(dmitry, card, "Коммунальные платежи", "320", "Квартира и счета", day);
        fixtures.expense(dmitry, card, "Кофейня у работы", "236", "Кафе и рестораны", day);
        fixtures.expense(dmitry, card, "Такси", "12.40", "Транспорт", day);
        budgetService.setLimit(dmitry.getId(), fixtures.standardCategory("Кафе и рестораны").getId(),
                lastMonth, new BigDecimal("200"));
    }

    @Test
    void reportTextContainsSumsTopCategoriesAndOverBudget() {
        String text = reportService.reportText(dmitry.getId(), lastMonth, false).replace(MoneyFormat.NBSP, ' ');

        assertThat(text)
                .startsWith("Отчёт за ")
                .contains("Доходы: 2 400,00 BYN")
                .contains("Расходы: 1 066,50 BYN")
                .contains("1. Продукты — 498,10")
                .contains("2. Квартира и счета — 320,00")
                .contains("3. Кафе и рестораны — 236,00")
                .doesNotContain("Транспорт")
                .contains("Бюджеты: превышен «Кафе и рестораны» на 18%");
    }

    @Test
    void sendMonthlyReportSendsToChatAndRemembersMonth() {
        fixtures.linkTelegram(dmitry, 1001L);

        reportService.sendMonthlyReport(dmitry.getId(), lastMonth);

        assertThat(telegram.messages()).hasSize(1);
        assertThat(telegram.messages().get(0).chatId()).isEqualTo(1001L);
        assertThat(telegram.messages().get(0).text()).contains("Больше всего потратили на:");
        assertThat(settingsRepository.findById(dmitry.getId()).orElseThrow().getLastReportMonth())
                .isEqualTo(lastMonth.atDay(1));
    }

    @Test
    void familyReportIsSentAfterPersonalOne() {
        User anna = fixtures.createUser("Анна", "anna@test.by");
        fixtures.family("Семья Ивановых", dmitry, anna);
        fixtures.linkTelegram(dmitry, 1001L);

        reportService.sendMonthlyReport(dmitry.getId(), lastMonth);

        assertThat(telegram.messages()).extracting(m -> m.text().lines().findFirst().orElseThrow())
                .containsExactly("Отчёт за " + DateFormat.monthLower(lastMonth),
                        "Отчёт семьи за " + DateFormat.monthLower(lastMonth));
    }

    @Test
    void schedulerSendsOnceAtReportHourInUserTimeZone() {
        fixtures.linkTelegram(dmitry, 1001L);
        User user = userRepository.findById(dmitry.getId()).orElseThrow();
        user.setTimeZone("Asia/Tokyo");
        NotificationSettings settings = settingsRepository.findById(dmitry.getId()).orElseThrow();
        settings.setReportDay((short) 1);
        settings.setReportHour((short) 10);
        settingsRepository.flush();

        // 1-е число месяца после отчётного, 10:00 по Токио
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        Clock tenInTokyo = Clock.fixed(lastMonth.plusMonths(1).atDay(1).atTime(10, 0).atZone(tokyo).toInstant(), tokyo);
        Clock nineInTokyo = Clock.offset(tenInTokyo, Duration.ofHours(-1));

        new MonthlyReportScheduler(settingsRepository, reportService, nineInTokyo).sendDueReports();
        assertThat(telegram.messages()).isEmpty();

        MonthlyReportScheduler atTen = new MonthlyReportScheduler(settingsRepository, reportService, tenInTokyo);
        atTen.sendDueReports();
        atTen.sendDueReports();   // повторный запуск в тот же час не отправляет отчёт ещё раз
        assertThat(telegram.messages()).hasSize(1);
    }

    @Test
    void balanceCommandListsAccountsAndTotal() {
        String text = reportService.balanceText(dmitry.getId()).replace(MoneyFormat.NBSP, ' ');

        // Наличные 0 + карта 1000 + 2400 - 1066,50
        assertThat(text).contains("Зарплатная карта — 2 333,50 BYN").contains("Наличные — 0,00 BYN")
                .contains("Итого: 2 333,50 BYN");
    }

    @Test
    void todayCommandShowsSpentToday() {
        fixtures.expense(dmitry, card, "Супермаркет", "46.80", "Продукты", transactionService.today(dmitry.getId()));

        String text = reportService.todayText(dmitry.getId()).replace(MoneyFormat.NBSP, ' ');

        assertThat(text).contains("Потрачено: 46,80 BYN").contains("Можно потратить сегодня:");
    }
}
