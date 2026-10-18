package com.fintrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Планировщик (ежемесячные отчёты) и асинхронные слушатели (уведомления о бюджете).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}
