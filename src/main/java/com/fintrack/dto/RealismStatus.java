package com.fintrack.dto;

/** Оценка реалистичности плана: заголовок, пояснение и «всё хорошо» (зелёный) или предупреждение. */
public record RealismStatus(String title, String text, boolean ok) {
}
