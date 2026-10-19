package com.fintrack.dto;

import com.fintrack.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryForm {

    @NotBlank(message = "Введите название категории")
    @Size(max = 60, message = "Название — не длиннее 60 символов")
    private String name;

    @NotNull(message = "Выберите тип категории")
    private CategoryType type = CategoryType.EXPENSE;
}
