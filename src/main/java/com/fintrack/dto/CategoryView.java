package com.fintrack.dto;

import com.fintrack.entity.CategoryType;

/**
 * Категория для отображения.
 */
public record CategoryView(Long id, String name, CategoryType type, String typeLabel,
                           boolean standard, boolean archived) {
}
