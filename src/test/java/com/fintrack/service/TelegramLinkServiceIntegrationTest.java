package com.fintrack.service;

import com.fintrack.entity.TelegramLink;
import com.fintrack.entity.User;
import com.fintrack.repository.TelegramLinkRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramLinkServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private TelegramLinkService linkService;

    @Autowired
    private TelegramLinkRepository linkRepository;

    private User dmitry;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
    }

    @Test
    void codeHasEightDigitsAndLinksChatOnce() {
        String code = linkService.createCode(dmitry.getId()).getLinkCode();
        assertThat(code).matches("\\d{8}");

        String reply = linkService.link(code, 1001L);

        assertThat(reply).startsWith("Готово!");
        TelegramLink link = linkRepository.findByUser_Id(dmitry.getId()).orElseThrow();
        assertThat(link.getChatId()).isEqualTo(1001L);
        assertThat(link.getLinkCode()).isNull();
        assertThat(link.getLinkedAt()).isNotNull();
        assertThat(linkService.findUserIdByChat(1001L)).contains(dmitry.getId());
        // код одноразовый: второй раз он уже не работает
        assertThat(linkService.link(code, 2002L)).startsWith("Код недействителен");
    }

    @Test
    void wrongCodeIsRejected() {
        linkService.createCode(dmitry.getId());

        assertThat(linkService.link("00000000", 1001L)).startsWith("Код недействителен");
        assertThat(linkService.findUserIdByChat(1001L)).isEmpty();
    }

    @Test
    void expiredCodeIsRejected() {
        TelegramLink link = linkService.createCode(dmitry.getId());
        link.setCodeExpiresAt(OffsetDateTime.now().minusMinutes(1));
        linkRepository.flush();

        assertThat(linkService.link(link.getLinkCode(), 1001L)).startsWith("Код недействителен");
        assertThat(linkService.findUserIdByChat(1001L)).isEmpty();
    }

    @Test
    void chatLinkedToAnotherUserCannotBeTaken() {
        User anna = fixtures.createUser("Анна", "anna@test.by");
        fixtures.linkTelegram(dmitry, 1001L);
        String annaCode = linkService.createCode(anna.getId()).getLinkCode();

        assertThat(linkService.link(annaCode, 1001L)).contains("уже подключён к другому аккаунту");
        assertThat(linkService.findUserIdByChat(1001L)).contains(dmitry.getId());
    }

    @Test
    void newCodeReplacesOldOne() {
        String first = linkService.createCode(dmitry.getId()).getLinkCode();
        String second = linkService.createCode(dmitry.getId()).getLinkCode();

        assertThat(second).isNotEqualTo(first);
        assertThat(linkService.link(first, 1001L)).startsWith("Код недействителен");
        assertThat(linkService.link(second, 1001L)).startsWith("Готово!");
    }

    @Test
    void unlinkForgetsChat() {
        fixtures.linkTelegram(dmitry, 1001L);

        linkService.unlink(dmitry.getId());

        assertThat(linkService.findUserIdByChat(1001L)).isEmpty();
        assertThat(linkService.status(dmitry.getId()).linked()).isFalse();
    }

    @Test
    void statusShowsCodeWithSpaceUntilLinked() {
        linkService.createCode(dmitry.getId());
        assertThat(linkService.status(dmitry.getId()).code()).matches("\\d{4} \\d{4}");

        fixtures.linkTelegram(dmitry, 1001L);
        assertThat(linkService.status(dmitry.getId()).linked()).isTrue();
        assertThat(linkService.status(dmitry.getId()).linkedText()).startsWith("Подключено ");
    }
}
