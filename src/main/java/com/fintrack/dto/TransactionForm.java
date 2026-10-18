package com.fintrack.dto;

import com.fintrack.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Форма операции (доход, расход или перевод между своими счетами).
 */
@Getter
@Setter
@NoArgsConstructor
public class TransactionForm {

    @NotNull(message = "Выберите тип операции")
    private TransactionType type = TransactionType.EXPENSE;

    @NotBlank(message = "Введите название")
    @Size(max = 120, message = "Название — не длиннее 120 символов")
    private String title;

    @NotNull(message = "Введите сумму")
    @DecimalMin(value = "0.01", message = "Сумма должна быть больше нуля")
    @Digits(integer = 17, fraction = 2, message = "Не больше двух знаков после запятой")
    private BigDecimal amount;

    @NotNull(message = "Выберите счёт")
    private Long accountId;

    /** Счёт зачисления — только для перевода. */
    private Long targetAccountId;

    /** Категория — для дохода и расхода. */
    private Long categoryId;

    @NotNull(message = "Укажите дату")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate operationDate;

    @Size(max = 500, message = "Комментарий — не длиннее 500 символов")
    private String comment;
}
