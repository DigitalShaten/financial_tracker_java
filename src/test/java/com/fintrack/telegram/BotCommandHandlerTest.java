package com.fintrack.telegram;

import com.fintrack.service.MonthlyReportService;
import com.fintrack.service.TelegramLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BotCommandHandlerTest {

    private static final long CHAT = 555L;

    private TelegramLinkService linkService;
    private MonthlyReportService reportService;
    private BotCommandHandler handler;

    @BeforeEach
    void setUp() {
        linkService = mock(TelegramLinkService.class);
        reportService = mock(MonthlyReportService.class);
        handler = new BotCommandHandler(linkService, reportService);
    }

    @Test
    void unlinkedChatGetsOnlyInstructionAndNoData() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.empty());

        assertThat(handler.handle(CHAT, "/balance")).isEqualTo(BotCommandHandler.NOT_LINKED);
        assertThat(handler.handle(CHAT, "/today")).isEqualTo(BotCommandHandler.NOT_LINKED);
        assertThat(handler.handle(CHAT, "привет")).isEqualTo(BotCommandHandler.NOT_LINKED);

        verifyNoInteractions(reportService);
        verify(linkService, never()).unlink(anyLong());
    }

    @Test
    void startWithCodeLinksChat() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.empty());
        when(linkService.link("48219037", CHAT)).thenReturn("Готово!");

        assertThat(handler.handle(CHAT, "/start 48219037")).isEqualTo("Готово!");
    }

    @Test
    void plainCodeWithSpaceAlsoLinksChat() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.empty());
        when(linkService.link("48219037", CHAT)).thenReturn("Готово!");

        assertThat(handler.handle(CHAT, "4821 9037")).isEqualTo("Готово!");
    }

    @Test
    void startWithoutCodeShowsHelp() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.empty());

        assertThat(handler.handle(CHAT, "/start")).isEqualTo(BotCommandHandler.HELP);
    }

    @Test
    void linkedChatGetsBalanceTodayAndCanStop() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.of(7L));
        when(reportService.balanceText(7L)).thenReturn("Балансы счетов");
        when(reportService.todayText(7L)).thenReturn("Сегодня");
        when(linkService.unlink(7L)).thenReturn("Бот отключён");

        assertThat(handler.handle(CHAT, "/balance")).isEqualTo("Балансы счетов");
        assertThat(handler.handle(CHAT, "/TODAY")).isEqualTo("Сегодня");
        assertThat(handler.handle(CHAT, "/stop")).isEqualTo("Бот отключён");
        assertThat(handler.handle(CHAT, "/unknown")).isEqualTo(BotCommandHandler.HELP);
    }
}
