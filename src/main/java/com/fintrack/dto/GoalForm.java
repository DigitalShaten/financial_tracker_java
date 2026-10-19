package com.fintrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoalForm extends GoalPlanForm {

    @NotBlank(message = "Введите название цели")
    @Size(max = 100, message = "Название — не длиннее 100 символов")
    private String name;

    private boolean family;
}
