package com.fintrack.dto;

/** Кнопка переключателя «Я / {имя} / Вместе». */
public record ViewOption(String label, String value, boolean active) {
}
