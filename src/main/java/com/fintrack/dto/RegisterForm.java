package com.fintrack.dto;

import com.fintrack.util.Choices;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterForm {

    @NotBlank(message = "Введите имя")
    @Size(max = 100, message = "Имя — не длиннее 100 символов")
    private String name;

    @NotBlank(message = "Введите e-mail")
    @Email(message = "Проверьте e-mail: например, anna@mail.by")
    @Size(max = 255, message = "E-mail слишком длинный")
    private String email;

    @NotBlank(message = "Введите пароль")
    @Size(min = 8, max = 72, message = "Пароль — от 8 до 72 символов")
    private String password;

    @NotBlank(message = "Выберите валюту")
    @Pattern(regexp = Choices.CURRENCY_PATTERN, message = "Выберите валюту из списка")
    private String currency = "BYN";
}
