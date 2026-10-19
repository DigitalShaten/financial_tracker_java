package com.fintrack.dto;

import com.fintrack.entity.CategoryType;

public record AdminCategoryView(Long id, String name, CategoryType type, boolean archived, boolean used) {
}
