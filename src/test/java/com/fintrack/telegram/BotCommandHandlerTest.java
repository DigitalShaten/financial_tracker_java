package com.fintrack.telegram;

import com.fintrack.dto.CategoryView;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.exception.NotFoundException;
import com.fintrack.service.BotExpenseService;
import com.fintrack.service.MonthlyReportService;
import com.fintrack.service.TelegramLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Команды бота без Spring: сервисы подменены моками.
 */
class BotCommandHandlerTest {

    private static final long CHAT = 555L;
    private static final Long USER = 7L;

    private TelegramLinkService linkService;
    private MonthlyReportService reportService;
    private BotExpenseService expenseService;
    private BotCommandHandler handler;

    @BeforeEach
    void setUp() {
        linkService = mock(TelegramLinkService.class);
        reportService = mock(MonthlyReportService.class);
        expenseService = mock(BotExpenseService.class);
        handler = new BotCommandHandler(linkService, reportService, expenseService,
                new ExpenseDrafts(Clock.systemUTC()));
    }

    private void linked() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.of(USER));
    }

    @Test
    void unlinkedChatGetsOnlyInstructionAndNoData() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.empty());

        assertThat(handler.handle(CHAT, "/balance").text()).isEqualTo(BotCommandHandler.NOT_LINKED);
        assertThat(handler.handle(CHAT, "/add").text()).isEqualTo(BotCommandHandler.NOT_LINKED);
        assertThat(handler.handle(CHAT, "привет").text()).isEqualTo(BotCommandHandler.NOT_LINKED);
        assertThat(handler.handleButton(CHAT, "cat:5").text()).isEqualTo(BotCommandHandler.NOT_LINKED);

        verifyNoInteractions(reportService, expenseService);
        verify(linkService, never()).unlink(anyLong());
    }

    @Test
    void startWithCodeLinksChat() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.empty());
        when(linkService.link("48219037", CHAT)).thenReturn("Готово!");

        assertThat(handler.handle(CHAT, "/start 48219037").text()).isEqualTo("Готово!");
        assertThat(handler.handle(CHAT, "4821 9037").text()).isEqualTo("Готово!");
    }

    @Test
    void startWithoutCodeShowsHelp() {
        when(linkService.findUserIdByChat(CHAT)).thenReturn(Optional.empty());

        assertThat(handler.handle(CHAT, "/start").text()).isEqualTo(BotCommandHandler.HELP);
    }

    @Test
    void linkedChatGetsBalanceTodayAndCanStop() {
        linked();
        when(reportService.balanceText(USER)).thenReturn("Балансы счетов");
        when(reportService.todayText(USER)).thenReturn("Сегодня");
        when(linkService.unlink(USER)).thenReturn("Бот отключён");

        assertThat(handler.handle(CHAT, "/balance").text()).isEqualTo("Балансы счетов");
        assertThat(handler.handle(CHAT, "/TODAY").text()).isEqualTo("Сегодня");
        assertThat(handler.handle(CHAT, "/stop").text()).isEqualTo("Бот отключён");
        assertThat(handler.handle(CHAT, "/unknown").text()).isEqualTo(BotCommandHandler.HELP);
    }

    @Test
    void addShowsCategoriesAsButtons() {
        linked();
        when(expenseService.categories(USER)).thenReturn(List.of(
                view(1L, "Продукты"), view(2L, "Транспорт"), view(3L, "Кафе и рестораны")));

        BotReply reply = handler.handle(CHAT, "/add");

        assertThat(reply.text()).isEqualTo("Выберите категорию расхода:");
        assertThat(reply.keyboard()).hasSize(3);   // две строки категорий по 2 кнопки и "Отмена"
        assertThat(reply.keyboard().get(0)).extracting(BotButton::data).containsExactly("cat:1", "cat:2");
        assertThat(reply.keyboard().get(2)).extracting(BotButton::text).containsExactly("Отмена");
    }

    @Test
    void fullExpenseFlowWithComment() {
        linked();
        when(expenseService.category(USER, 1L)).thenReturn(category(1L, "Продукты"));
        when(expenseService.addExpense(eq(USER), eq(1L), any(), eq("Обед"))).thenReturn("Записано");

        assertThat(handler.handleButton(CHAT, "cat:1").text()).contains("Продукты").contains("сумму");
        assertThat(handler.handle(CHAT, "abc").text()).isEqualTo(BotCommandHandler.BAD_AMOUNT);
        assertThat(handler.handle(CHAT, "0").text()).isEqualTo(BotCommandHandler.BAD_AMOUNT);

        BotReply askComment = handler.handle(CHAT, "46,80");
        assertThat(askComment.text()).isEqualTo(BotCommandHandler.ASK_COMMENT);
        assertThat(askComment.keyboard().get(0)).extracting(BotButton::data).containsExactly("skip");

        assertThat(handler.handle(CHAT, "Обед").text()).isEqualTo("Записано");
        verify(expenseService).addExpense(USER, 1L, new BigDecimal("46.80"), "Обед");
        // ввод закончен - следующее сообщение уже не комментарий
        assertThat(handler.handle(CHAT, "ещё текст").text()).isEqualTo(BotCommandHandler.HELP);
    }

    @Test
    void skipButtonSavesWithoutComment() {
        linked();
        when(expenseService.category(USER, 2L)).thenReturn(category(2L, "Транспорт"));
        when(expenseService.addExpense(eq(USER), eq(2L), any(), isNull())).thenReturn("Записано");

        handler.handleButton(CHAT, "cat:2");
        handler.handle(CHAT, "12");

        assertThat(handler.handleButton(CHAT, "skip").text()).isEqualTo("Записано");
        verify(expenseService).addExpense(USER, 2L, new BigDecimal("12"), null);
    }

    @Test
    void eightDigitAmountIsNotTakenForLinkCode() {
        linked();
        when(expenseService.category(USER, 1L)).thenReturn(category(1L, "Продукты"));

        handler.handleButton(CHAT, "cat:1");

        assertThat(handler.handle(CHAT, "10000000").text()).isEqualTo(BotCommandHandler.ASK_COMMENT);
        verify(linkService, never()).link(any(), anyLong());
    }

    @Test
    void cancelAndCommandsStopTheFlow() {
        linked();
        when(expenseService.category(USER, 1L)).thenReturn(category(1L, "Продукты"));

        handler.handleButton(CHAT, "cat:1");
        assertThat(handler.handle(CHAT, "/cancel").text()).contains("отменён");
        assertThat(handler.handle(CHAT, "46").text()).isEqualTo(BotCommandHandler.HELP);

        handler.handleButton(CHAT, "cat:1");
        assertThat(handler.handleButton(CHAT, "cancel").text()).contains("отменён");
        verify(expenseService, never()).addExpense(anyLong(), anyLong(), any(), any());
    }

    @Test
    void foreignOrBrokenCategoryButtonIsRejected() {
        linked();
        when(expenseService.category(USER, 99L)).thenThrow(new NotFoundException("Категория не найдена"));

        assertThat(handler.handleButton(CHAT, "cat:99").text()).contains("Такой категории нет");
        assertThat(handler.handleButton(CHAT, "cat:abc").text()).contains("Такой категории нет");
        assertThat(handler.handle(CHAT, "46").text()).isEqualTo(BotCommandHandler.HELP);
    }

    @Test
    void amountParsing() {
        assertThat(BotCommandHandler.parseAmount("46,80")).isEqualByComparingTo("46.80");
        assertThat(BotCommandHandler.parseAmount("46.8")).isEqualByComparingTo("46.80");
        assertThat(BotCommandHandler.parseAmount("1 286,40")).isEqualByComparingTo("1286.40");
        assertThat(BotCommandHandler.parseAmount("-5")).isNull();
        assertThat(BotCommandHandler.parseAmount("1,234")).isNull();
        assertThat(BotCommandHandler.parseAmount("сто")).isNull();
    }

    private static CategoryView view(Long id, String name) {
        return new CategoryView(id, name, CategoryType.EXPENSE, "Расход", true, false);
    }

    private static Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType(CategoryType.EXPENSE);
        return c;
    }
}
