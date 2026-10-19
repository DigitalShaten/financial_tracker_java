package com.fintrack.controller;

import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.User;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.repository.TelegramLinkRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelegramPageIntegrationTest extends IntegrationTest {

    @Autowired
    private TelegramLinkRepository linkRepository;

    @Autowired
    private NotificationSettingsRepository settingsRepository;

    private User dmitry;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
    }

    @Test
    void notLinkedPageShowsInstructionAndDisabledTestButton() throws Exception {
        mvc.perform(get("/telegram").with(user(fixtures.principalOf(dmitry))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Бот ещё не подключён")))
                .andExpect(content().string(containsString("@Fin_track_mega_bot")))
                .andExpect(content().string(containsString("Получить код")))
                .andExpect(content().string(containsString("Выводы и советы от ИИ")))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(containsString("class=\"bubble num report\"")));
    }

    @Test
    void linkCodeIsShownOnPage() throws Exception {
        var me = fixtures.principalOf(dmitry);
        mvc.perform(post("/telegram/link-code").with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/telegram"));

        String code = linkRepository.findByUser_Id(dmitry.getId()).orElseThrow().getLinkCode();
        mvc.perform(get("/telegram").with(user(me)))
                .andExpect(content().string(containsString(code.substring(0, 4) + " " + code.substring(4))));
    }

    @Test
    void settingsAreSavedAndValidated() throws Exception {
        var me = fixtures.principalOf(dmitry);
        mvc.perform(post("/telegram/settings").with(user(me)).with(csrf())
                        .param("monthlyReport", "true").param("reportDay", "29").param("reportHour", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("День — от 1 до 28")));

        mvc.perform(post("/telegram/settings").with(user(me)).with(csrf())
                        .param("monthlyReport", "true").param("reportDay", "5").param("reportHour", "20")
                        .param("budgetAlerts", "true"))
                .andExpect(redirectedUrl("/telegram"));

        NotificationSettings s = settingsRepository.findById(dmitry.getId()).orElseThrow();
        assertThat(s.isMonthlyReport()).isTrue();
        assertThat(s.getReportDay()).isEqualTo((short) 5);
        assertThat(s.getReportHour()).isEqualTo((short) 20);
        assertThat(s.isFamilyReport()).isFalse();
        assertThat(s.isBudgetAlerts()).isTrue();
    }

    @Test
    void testReportNeedsLinkedBot() throws Exception {
        var me = fixtures.principalOf(dmitry);
        mvc.perform(post("/telegram/test-report").with(user(me)).with(csrf()))
                .andExpect(flash().attribute("toast", "Сначала подключите Telegram-бота"));
        assertThat(telegram.messages()).isEmpty();

        fixtures.linkTelegram(dmitry, 1001L);
        mvc.perform(post("/telegram/test-report").with(user(me)).with(csrf()))
                .andExpect(flash().attribute("toast", "Тестовый отчёт отправлен в Telegram"));
        assertThat(telegram.messages()).singleElement()
                .satisfies(m -> assertThat(m.chatId()).isEqualTo(1001L));
    }

    @Test
    void linkedPageAllowsUnlink() throws Exception {
        var me = fixtures.principalOf(dmitry);
        fixtures.linkTelegram(dmitry, 1001L);
        mvc.perform(get("/telegram").with(user(me)))
                .andExpect(content().string(containsString("бот подключён")))
                .andExpect(content().string(not(containsString("Получить код"))));

        mvc.perform(post("/telegram/unlink").with(user(me)).with(csrf())).andExpect(redirectedUrl("/telegram"));

        assertThat(linkRepository.findByUser_Id(dmitry.getId()).orElseThrow().getChatId()).isNull();
    }

    @Test
    void familyMembersStatusesAreShown() throws Exception {
        User anna = fixtures.createUser("Анна", "anna@test.by");
        fixtures.family("Семья Ивановых", dmitry, anna);
        fixtures.linkTelegram(anna, 2002L);

        mvc.perform(get("/telegram").with(user(fixtures.principalOf(dmitry))))
                .andExpect(content().string(containsString("Анна")))
                .andExpect(content().string(containsString("Telegram подключён")));
    }
}
