package com.fintrack.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotificationSettingsForm {

    private boolean monthlyReport;

    @NotNull(message = "Выберите день")
    @Min(value = 1, message = "День — от 1 до 28")
    @Max(value = 28, message = "День — от 1 до 28")
    private Integer reportDay;

    @NotNull(message = "Выберите час")
    @Min(value = 0, message = "Час — от 0 до 23")
    @Max(value = 23, message = "Час — от 0 до 23")
    private Integer reportHour;

    private boolean familyReport;

    private boolean budgetAlerts;
}
