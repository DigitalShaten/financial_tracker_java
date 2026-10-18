package com.fintrack.dto;

import com.fintrack.entity.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Форма счёта. Начальный баланс используется только при создании.
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountForm {

    @NotBlank(message = "Введите название счёта")
    @Size(max = 100, message = "Название — не длиннее 100 символов")
    private String name;

    @NotNull(message = "Выберите тип счёта")
    private AccountType type = AccountType.CARD;

    @Digits(integer = 17, fraction = 2, message = "Не больше двух знаков после запятой")
    private BigDecimal initialBalance;

    private boolean sharedWithFamily = true;
}
