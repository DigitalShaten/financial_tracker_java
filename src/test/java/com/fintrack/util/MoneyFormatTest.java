package com.fintrack.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatTest {

    @Test
    void formatsWithNonBreakingSpaceAndComma() {
        assertThat(MoneyFormat.format(new BigDecimal("1286.4"), "BYN")).isEqualTo("1 286,40 BYN");
    }

    @Test
    void roundsToCents() {
        assertThat(MoneyFormat.format(new BigDecimal("0.005"))).isEqualTo("0,01");
        assertThat(MoneyFormat.format(new BigDecimal("1234567.891"))).isEqualTo("1 234 567,89");
    }

    @Test
    void wholeFormatHasNoCents() {
        assertThat(MoneyFormat.formatWhole(new BigDecimal("121246.75"))).isEqualTo("121 247");
    }
}
