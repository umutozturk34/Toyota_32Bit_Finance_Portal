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
    void scaleAndComputeChangeCalculatesPositiveChange() {
        Stock stock = createStock("55", "50", "51", "56", "49");

        stock.scaleAndComputeChange(4);

        assertThat(stock.getPriceChangeAmount()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(stock.getPriceChangePercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(stock.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("55.0000"));
        assertThat(stock.getCurrentPrice().scale()).isEqualTo(4);
    }

    @Test
    void scaleAndComputeChangeCalculatesNegativeChange() {
        Stock stock = createStock("45", "50", "49", "50", "44");

        stock.scaleAndComputeChange(4);

        assertThat(stock.getPriceChangeAmount()).isEqualByComparingTo(new BigDecimal("-5.0000"));
        assertThat(stock.getPriceChangePercent()).isEqualByComparingTo(new BigDecimal("-10.0000"));
    }

    @Test
    void scaleAndComputeChangeNullPreviousCloseNullsOutChange() {
        Stock stock = createStock("55", null, "51", "56", "49");

        stock.scaleAndComputeChange(4);

        assertThat(stock.getPriceChangeAmount()).isNull();
        assertThat(stock.getPriceChangePercent()).isNull();
    }

    @Test
    void scaleAndComputeChangeZeroPreviousCloseNullsOutChange() {
        Stock stock = createStock("55", "0", "51", "56", "49");

        stock.scaleAndComputeChange(4);

        assertThat(stock.getPriceChangeAmount()).isNull();
        assertThat(stock.getPriceChangePercent()).isNull();
    }

    @Test
    void scaleAndComputeChangeNullCurrentPriceNullsOutChange() {
        Stock stock = createStock(null, "50", "49", "50", "48");

        stock.scaleAndComputeChange(4);

        assertThat(stock.getPriceChangeAmount()).isNull();
        assertThat(stock.getPriceChangePercent()).isNull();
    }

    @Test
    void scaleAndComputeChangeScalesAllPriceFields() {
        Stock stock = createStock("55.123456", "50.987654", "51.111111", "56.666666", "49.333333");

        stock.scaleAndComputeChange(4);

        assertThat(stock.getCurrentPrice().scale()).isEqualTo(4);
        assertThat(stock.getPreviousClose().scale()).isEqualTo(4);
        assertThat(stock.getOpenPrice().scale()).isEqualTo(4);
        assertThat(stock.getDayHigh().scale()).isEqualTo(4);
        assertThat(stock.getDayLow().scale()).isEqualTo(4);
    }

    @Test
    void scaleAndComputeChangeFractionalPercent() {
        Stock stock = createStock("103", "100", "100", "104", "99");

        stock.scaleAndComputeChange(4);

        assertThat(stock.getPriceChangeAmount()).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(stock.getPriceChangePercent()).isEqualByComparingTo(new BigDecimal("3.0000"));
    }

    @Test
    void scaleAndComputeChangeNullFieldsStayNull() {
        Stock stock = createStock("55", "50", null, null, null);

        stock.scaleAndComputeChange(4);

        assertThat(stock.getOpenPrice()).isNull();
        assertThat(stock.getDayHigh()).isNull();
        assertThat(stock.getDayLow()).isNull();
        assertThat(stock.getPriceChangeAmount()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }
}
