package com.fintrack.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Для страниц администратора: статус сервиса из Actuator health - показывается в шапке.
 */
@ControllerAdvice(basePackages = "com.fintrack.controller.admin")
@RequiredArgsConstructor
public class AdminModelAdvice {

    private final ObjectProvider<HealthEndpoint> healthEndpoint;

    /** Статус для шапки: {@code up} - всё в порядке, {@code text} - подпись. */
    public record ServiceStatus(boolean up, String text) {
    }

    @ModelAttribute("serviceStatus")
    public ServiceStatus serviceStatus() {
        HealthEndpoint endpoint = healthEndpoint.getIfAvailable();
        if (endpoint == null) {
            return new ServiceStatus(false, "Статус сервиса недоступен");
        }
        Status status = endpoint.health().getStatus();
        return Status.UP.equals(status)
                ? new ServiceStatus(true, "Сервис работает")
                : new ServiceStatus(false, "Сервис: " + status.getCode());
    }
}
