package com.fintrack.service;

import com.fintrack.dto.NotificationSettingsForm;
import com.fintrack.dto.TelegramStatus;
import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.TelegramLink;
import com.fintrack.entity.User;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.repository.TelegramLinkRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.util.DateFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Привязка Telegram-чата к аккаунту одноразовым кодом из 8 цифр (действует 15 минут).
 * Один чат - один пользователь. Здесь же - настройки уведомлений со страницы "Telegram-бот".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    private static final Duration CODE_TTL = Duration.ofMinutes(15);

    private final TelegramLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Новый код привязки (старый код при этом перестаёт работать). */
    @Transactional
    public TelegramLink createCode(Long userId) {
        TelegramLink link = linkRepository.findByUser_Id(userId).orElseGet(() -> {
            TelegramLink l = new TelegramLink();
            l.setUser(userRepository.getReferenceById(userId));
            return l;
        });
        link.setLinkCode(uniqueCode());
        link.setCodeExpiresAt(now().plus(CODE_TTL));
        return linkRepository.save(link);
    }

    /** Привязка чата по коду. Возвращает ответ бота. */
    @Transactional
    public String link(String code, long chatId) {
        Optional<TelegramLink> found = linkRepository.findByLinkCode(code.trim())
                .filter(l -> l.getCodeExpiresAt() != null && l.getCodeExpiresAt().isAfter(now()));
        if (found.isEmpty()) {
            return "Код недействителен или истёк. Получите новый код на сайте в разделе «Telegram-бот».";
        }
        TelegramLink link = found.get();
        Optional<TelegramLink> chatOwner = linkRepository.findByChatId(chatId);
        if (chatOwner.isPresent() && !chatOwner.get().getId().equals(link.getId())) {
            return "Этот чат уже подключён к другому аккаунту FinTrack. Отправьте /stop, чтобы отключить его.";
        }
        link.setChatId(chatId);
        link.setLinkCode(null);
        link.setCodeExpiresAt(null);
        link.setLinkedAt(now());
        log.info("Telegram подключён для пользователя {}", link.getUser().getId());
        return "Готово! Аккаунт " + link.getUser().getName() + " подключён. Отчёт за месяц будет приходить сюда.\n\n"
                + "Команды: /balance — балансы, /today — траты за сегодня, /stop — отключить.";
    }

    /** Отвязка чата. Возвращает ответ бота. */
    @Transactional
    public String unlink(Long userId) {
        linkRepository.findByUser_Id(userId).ifPresent(link -> {
            link.setChatId(null);
            link.setLinkedAt(null);
            link.setLinkCode(null);
            link.setCodeExpiresAt(null);
        });
        return "Бот отключён от аккаунта. Чтобы подключить снова, получите код на сайте.";
    }

    @Transactional(readOnly = true)
    public Optional<Long> findUserIdByChat(long chatId) {
        return linkRepository.findByChatId(chatId).map(l -> l.getUser().getId());
    }

    /** Чат пользователя, если бот привязан. */
    @Transactional(readOnly = true)
    public Optional<Long> chatOf(Long userId) {
        return linkRepository.findByUser_Id(userId).map(TelegramLink::getChatId);
    }

    @Transactional(readOnly = true)
    public TelegramStatus status(Long userId) {
        ZoneId zone = ZoneId.of(userRepository.findById(userId).map(User::getTimeZone).orElse("Europe/Minsk"));
        Optional<TelegramLink> link = linkRepository.findByUser_Id(userId);
        if (link.isPresent() && link.get().isLinked()) {
            return new TelegramStatus(true, "Подключено " + DateFormat.dayMonthYear(
                    link.get().getLinkedAt().atZoneSameInstant(zone).toLocalDate()), null, null);
        }
        return link.filter(l -> l.getLinkCode() != null && l.getCodeExpiresAt().isAfter(now()))
                .map(l -> new TelegramStatus(false, null, formatCode(l.getLinkCode()),
                        "Код одноразовый, действует до " + l.getCodeExpiresAt().atZoneSameInstant(zone)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))))
                .orElse(new TelegramStatus(false, null, null, null));
    }

    /** Текущие настройки уведомлений - для заполнения формы. */
    @Transactional(readOnly = true)
    public NotificationSettingsForm settingsForm(Long userId) {
        NotificationSettings s = settingsRepository.findById(userId).orElseThrow();
        NotificationSettingsForm form = new NotificationSettingsForm();
        form.setMonthlyReport(s.isMonthlyReport());
        form.setReportDay((int) s.getReportDay());
        form.setReportHour((int) s.getReportHour());
        form.setFamilyReport(s.isFamilyReport());
        form.setBudgetAlerts(s.isBudgetAlerts());
        return form;
    }

    /** Сохранение настроек (значения уже проверены аннотациями формы). */
    @Transactional
    public void updateSettings(Long userId, NotificationSettingsForm form) {
        NotificationSettings s = settingsRepository.findById(userId).orElseThrow();
        s.setMonthlyReport(form.isMonthlyReport());
        s.setReportDay(form.getReportDay().shortValue());
        s.setReportHour(form.getReportHour().shortValue());
        s.setFamilyReport(form.isFamilyReport());
        s.setBudgetAlerts(form.isBudgetAlerts());
    }

    /** "48219037" -> "4821 9037" - так код удобнее прочитать (бот принимает его без пробела). */
    private static String formatCode(String code) {
        return code.substring(0, 4) + " " + code.substring(4);
    }

    private String uniqueCode() {
        String code;
        do {
            code = String.format("%08d", random.nextInt(100_000_000));
        } while (linkRepository.findByLinkCode(code).isPresent());
        return code;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
