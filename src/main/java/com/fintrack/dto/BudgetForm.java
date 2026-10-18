package com.fintrack.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Лимит расходов по категории на месяц. Пустой лимит — убрать бюджет.
 */
@Getter
@Setter
@NoArgsConstructor
public class BudgetForm {

    @NotNull(message = "Выберите категорию")
    private Long categoryId;

    @NotNull(message = "Укажите месяц")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Месяц в формате ГГГГ-ММ")
    private String month;

    @DecimalMin(value = "0", message = "Лимит не может быть отрицательным")
    @Digits(integer = 17, fraction = 2, message = "Не больше двух знаков после запятой")
    private BigDecimal limitAmount;
}
