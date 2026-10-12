package com.fintrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationForm(
        @NotBlank(message = "Введите имя")
        @Size(max = 100)
        String name,

        @NotBlank
        @Email(message = "Некорректный e-mail")
        String email,

        @NotBlank
        @Size(min = 8, max = 72, message = "Пароль от 8 до 72 символов")
        String password,

        @NotBlank
        @Pattern(regexp = "BYN|RUB|EUR|USD", message = "Выберите валюту")
        String currency
) {
    public static RegistrationForm empty() {
        return new RegistrationForm("", "", "", "BYN");
    }
}
