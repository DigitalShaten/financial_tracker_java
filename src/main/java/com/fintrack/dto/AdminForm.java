package com.fintrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminForm {

    @NotBlank(message = "Введите имя")
    @Size(max = 100, message = "Имя — не длиннее 100 символов")
    private String name;

    @NotBlank(message = "Введите e-mail")
    @Email(message = "Проверьте e-mail: например, admin@mail.by")
    @Size(max = 255, message = "E-mail слишком длинный")
    private String email;

    private String password;
}
