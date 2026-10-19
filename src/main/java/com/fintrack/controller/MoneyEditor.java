package com.fintrack.controller;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;

public class MoneyEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        if (text == null || text.isBlank()) {
            setValue(null);
            return;
        }
        String normalized = text.replace(' ', ' ').replace(" ", "").replace(',', '.');
        try {
            setValue(new BigDecimal(normalized));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Не число: " + text, e);
        }
    }

    @Override
    public String getAsText() {
        Object value = getValue();
        return value == null ? "" : ((BigDecimal) value).toPlainString();
    }
}
