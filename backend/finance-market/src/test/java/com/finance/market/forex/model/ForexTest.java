package com.finance.market.forex.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ForexTest {

    private static final int SCALE = 4;
    private static final LocalDateTime DATA_TIMESTAMP = LocalDateTime.of(2026, 5, 11, 0, 0);

    @Test
    void should_dividePricesByUnit_when_unitIsHundred() {
        Forex forex = Forex.builder().currencyCode("JPY").build();

        forex.applyEvdsSnapshot(DATA_TIMESTAMP,
                new BigDecimal("28.7572"), new BigDecimal("28.9476"),
                new BigDecimal("28.6508"), new BigDecimal("29.0576"), 100, SCALE);

        assertThat(forex.getBuyingPrice()).isEqualByComparingTo(new BigDecimal("0.2876"));
        assertThat(forex.getSellingPrice()).isEqualByComparingTo(new BigDecimal("0.2895"));
        assertThat(forex.getEffectiveBuyingPrice()).isEqualByComparingTo(new BigDecimal("0.2865"));
        assertThat(forex.getEffectiveSellingPrice()).isEqualByComparingTo(new BigDecimal("0.2906"));
        assertThat(forex.getLastUpdated()).isEqualTo(DATA_TIMESTAMP);
    }

    @Test
    void should_keepRawPrices_when_unitIsOne() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        forex.applyEvdsSnapshot(DATA_TIMESTAMP,
                new BigDecimal("45.1900"), new BigDecimal("45.2714"),
                new BigDecimal("45.1584"), new BigDecimal("45.3393"), 1, SCALE);

        assertThat(forex.getBuyingPrice()).isEqualByComparingTo(new BigDecimal("45.1900"));
        assertThat(forex.getSellingPrice()).isEqualByComparingTo(new BigDecimal("45.2714"));
        assertThat(forex.getLastUpdated()).isEqualTo(DATA_TIMESTAMP);
    }

    @Test
    void should_preserveNullPrices_when_inputIsNull() {
        Forex forex = Forex.builder().currencyCode("XDR").build();

        forex.applyEvdsSnapshot(DATA_TIMESTAMP, new BigDecimal("62.1957"), null, null, null, 1, SCALE);

        assertThat(forex.getBuyingPrice()).isEqualByComparingTo(new BigDecimal("62.1957"));
        assertThat(forex.getSellingPrice()).isNull();
        assertThat(forex.getEffectiveBuyingPrice()).isNull();
        assertThat(forex.getEffectiveSellingPrice()).isNull();
    }

    @Test
    void should_returnTrue_when_isTradableWithBothPrices() {
        Forex forex = Forex.builder().currencyCode("USD")
                .buyingPrice(new BigDecimal("45.19")).sellingPrice(new BigDecimal("45.27")).build();

        assertThat(forex.isTradable()).isTrue();
    }

    @Test
    void should_returnFalse_when_isTradableWithMissingSellingPrice() {
        Forex forex = Forex.builder().currencyCode("XDR")
                .buyingPrice(new BigDecimal("62.20")).build();

        assertThat(forex.isTradable()).isFalse();
    }

    @Test
    void should_returnFalse_when_isTradableWithBothMissing() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        assertThat(forex.isTradable()).isFalse();
    }

    @Test
    void should_returnSellingPrice_when_getPriceTryCalled() {
        Forex forex = Forex.builder().currencyCode("USD")
                .sellingPrice(new BigDecimal("45.27"))
                .build();

        assertThat(forex.getPriceTry()).isEqualByComparingTo(new BigDecimal("45.27"));
    }

    @Test
    void should_returnCurrencyCode_when_getCodeCalled() {
        Forex forex = Forex.builder().currencyCode("EUR").build();

        assertThat(forex.getCode()).isEqualTo("EUR");
    }

    @Test
    void should_resolveNameWhenSet() {
        Forex forex = Forex.builder().currencyCode("USD").build();
        forex.setName("ABD Doları");

        assertThat(forex.resolveDisplayName()).isEqualTo("ABD Doları");
    }

    @Test
    void should_fallBackToCurrencyCode_when_nameMissing() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        assertThat(forex.resolveDisplayName()).isEqualTo("USD");
    }
}
