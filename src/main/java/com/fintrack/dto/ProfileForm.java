package com.fintrack.dto;

import com.fintrack.util.Choices;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Форма профиля: имя, валюта, часовой пояс.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProfileForm {

    @NotBlank(message = "Введите имя")
    @Size(max = 100, message = "Имя — не длиннее 100 символов")
    private String name;

    @NotBlank(message = "Выберите валюту")
    @Pattern(regexp = Choices.CURRENCY_PATTERN, message = "Выберите валюту из списка")
    private String currency;

    @NotBlank(message = "Выберите часовой пояс")
    private String timeZone;
}
