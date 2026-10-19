package com.fintrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeleteAccountForm {

    @NotBlank(message = "Введите пароль, чтобы подтвердить удаление")
    private String password;
}
