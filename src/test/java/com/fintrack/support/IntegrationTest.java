package com.fintrack.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import({TestFixtures.class, RecordingTelegramSender.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestFixtures fixtures;

    /** Сообщения, которые приложение "отправило" в Telegram. */
    @Autowired
    protected RecordingTelegramSender telegram;

    @BeforeEach
    void clearTelegramMessages() {
        telegram.clear();
    }
}
