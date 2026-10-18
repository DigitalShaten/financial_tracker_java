package com.fintrack.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Параметры плана цели — значения ползунков.
 */
@Getter
@Setter
@NoArgsConstructor
public class GoalPlanForm {

    @NotNull(message = "Укажите стоимость")
    @DecimalMin(value = "1", message = "Стоимость должна быть больше нуля")
    @Digits(integer = 17, fraction = 2, message = "Не больше двух знаков после запятой")
    private BigDecimal price;

    @NotNull(message = "Укажите срок")
    @Min(value = 1, message = "Срок — от 1 до 20 лет")
    @Max(value = 20, message = "Срок — от 1 до 20 лет")
    private Integer years = 1;

    @NotNull(message = "Укажите инфляцию")
    @DecimalMin(value = "0", message = "Инфляция — от 0 до 15%")
    @DecimalMax(value = "15", message = "Инфляция — от 0 до 15%")
    private BigDecimal inflation = BigDecimal.ZERO;

    @NotNull(message = "Укажите доходность")
    @DecimalMin(value = "0", message = "Доходность — от 0 до 15%")
    @DecimalMax(value = "15", message = "Доходность — от 0 до 15%")
    private BigDecimal rate = BigDecimal.ZERO;
}
