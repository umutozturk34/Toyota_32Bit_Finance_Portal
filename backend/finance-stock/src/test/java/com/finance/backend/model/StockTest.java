package com.finance.backend.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockTest {

    private Stock createStock(String current, String prevClose, String open, String high, String low) {
        Stock stock = new Stock();
        stock.setCurrentPrice(current != null ? new BigDecimal(current) : null);
        stock.setPreviousClose(prevClose != null ? new BigDecimal(prevClose) : null);
        stock.setOpenPrice(open != null ? new BigDecimal(open) : null);
        stock.setDayHigh(high != null ? new BigDecimal(high) : null);
        stock.setDayLow(low != null ? new BigDecimal(low) : null);
        return stock;
    }

    @Test
    void scaleOnlyScalesAllPriceFields() {
        Stock stock = createStock("55.123456", "50.987654", "51.111111", "56.666666", "49.333333");

        stock.scaleOnly(4);

        assertThat(stock.getCurrentPrice().scale()).isEqualTo(4);
        assertThat(stock.getPreviousClose().scale()).isEqualTo(4);
        assertThat(stock.getOpenPrice().scale()).isEqualTo(4);
        assertThat(stock.getDayHigh().scale()).isEqualTo(4);
        assertThat(stock.getDayLow().scale()).isEqualTo(4);
    }

    @Test
    void scaleOnlyScalesProvidedChangeFields() {
        Stock stock = createStock("55", "50", "51", "56", "49");
        stock.setPriceChangeAmount(new BigDecimal("5.123456"));
        stock.setPriceChangePercent(new BigDecimal("10.246912"));

        stock.scaleOnly(4);

        assertThat(stock.getPriceChangeAmount()).isEqualByComparingTo(new BigDecimal("5.1235"));
        assertThat(stock.getPriceChangePercent()).isEqualByComparingTo(new BigDecimal("10.2469"));
    }

    @Test
    void scaleOnlyPreservesNullChangeFields() {
        Stock stock = createStock("55", null, "51", "56", "49");

        stock.scaleOnly(4);

        assertThat(stock.getPriceChangeAmount()).isNull();
        assertThat(stock.getPriceChangePercent()).isNull();
    }

    @Test
    void scaleOnlyLeavesNullPriceFieldsNull() {
        Stock stock = createStock("55", "50", null, null, null);

        stock.scaleOnly(4);

        assertThat(stock.getOpenPrice()).isNull();
        assertThat(stock.getDayHigh()).isNull();
        assertThat(stock.getDayLow()).isNull();
    }
}
