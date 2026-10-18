package com.fintrack.dto;

/**
 * Счёт в выпадающем списке. {@code shared} нужен для подсказки видимости под выбором счёта.
 */
public record AccountOption(Long id, String name, boolean shared) {
}
