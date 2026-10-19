package com.fintrack.config;

import com.fintrack.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * При старте приложения создаёт первого администратора из {@code app.admin.email} / {@code app.admin.password}.
 */
@Component
@Slf4j
public class AdminInitializer implements ApplicationRunner {

    private final UserService userService;
    private final String email;
    private final String password;

    public AdminInitializer(UserService userService,
                            @Value("${app.admin.email}") String email,
                            @Value("${app.admin.password}") String password) {
        this.userService = userService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Пустой ADMIN_PASSWORD в .env дал бы администратора с пустым паролем - такого не создаём
        if (email == null || email.isBlank() || password == null || password.length() < 8) {
            log.warn("Первый администратор не создан: задайте ADMIN_EMAIL и ADMIN_PASSWORD (не короче 8 символов)");
            return;
        }
        userService.createAdminIfMissing(email, password);
    }
}
