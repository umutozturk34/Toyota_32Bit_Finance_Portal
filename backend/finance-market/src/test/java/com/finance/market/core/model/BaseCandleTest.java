package com.finance.market.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BaseCandleTest {

    private static class TestCandle extends BaseCandle {}

    @Test
    void scaleFields_appliesScaleToAllOhlcValues() {
        TestCandle candle = new TestCandle();
        candle.setOpen(new BigDecimal("1.12345"));
        candle.setHigh(new BigDecimal("1.23456"));
        candle.setLow(new BigDecimal("1.00000"));
        candle.setClose(new BigDecimal("1.20000"));

        candle.scaleFields(2);

        assertThat(candle.getOpen().scale()).isEqualTo(2);
        assertThat(candle.getHigh()).isEqualByComparingTo("1.23");
        assertThat(candle.getLow()).isEqualByComparingTo("1.00");
        assertThat(candle.getClose()).isEqualByComparingTo("1.20");
    }

    @Test
    void scaleFields_leavesNullFieldsUntouched() {
        TestCandle candle = new TestCandle();
        candle.setOpen(null);
        candle.setHigh(null);
        candle.setLow(null);
        candle.setClose(null);

        candle.scaleFields(4);

        assertThat(candle.getOpen()).isNull();
        assertThat(candle.getHigh()).isNull();
    }

    @Test
    void scaleAndNormalizeOhlc_raisesHigh_belowOpenOrClose() {
        TestCandle candle = new TestCandle();
        candle.setOpen(new BigDecimal("5"));
        candle.setHigh(new BigDecimal("4"));
        candle.setLow(new BigDecimal("3"));
        candle.setClose(new BigDecimal("6"));

        candle.scaleAndNormalizeOhlc(2);

        assertThat(candle.getHigh()).isEqualByComparingTo("6");
    }

    @Test
    void scaleAndNormalizeOhlc_lowersLow_aboveOpenOrClose() {
        TestCandle candle = new TestCandle();
        candle.setOpen(new BigDecimal("5"));
        candle.setHigh(new BigDecimal("8"));
        candle.setLow(new BigDecimal("7"));
        candle.setClose(new BigDecimal("4"));

        candle.scaleAndNormalizeOhlc(2);

        assertThat(candle.getLow()).isEqualByComparingTo("4");
    }

    @Test
    void scaleAndNormalizeOhlc_skipsNormalization_whenAnyFieldNull() {
        TestCandle candle = new TestCandle();
        candle.setOpen(new BigDecimal("5"));
        candle.setHigh(null);
        candle.setLow(new BigDecimal("3"));
        candle.setClose(new BigDecimal("6"));

        candle.scaleAndNormalizeOhlc(2);

        assertThat(candle.getHigh()).isNull();
        assertThat(candle.getLow()).isEqualByComparingTo("3.00");
    }
}
