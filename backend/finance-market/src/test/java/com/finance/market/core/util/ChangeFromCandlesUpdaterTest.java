package com.finance.market.core.util;

import com.finance.market.core.model.BaseAsset;
import com.finance.market.stock.model.Stock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeFromCandlesUpdaterTest {

    @Test
    void applyFromPriorCloseIfMissing_skips_whenSourceAlreadyProvidedNonZeroPercent() {
        // Arrange
        Stock stock = baseStock();
        stock.setChangePercent(new BigDecimal("1.25"));
        stock.setChangeAmount(new BigDecimal("0.50"));

        // Act
        boolean applied = ChangeFromCandlesUpdater.applyFromPriorCloseIfMissing(
                stock, new BigDecimal("100"), new BigDecimal("95"), 2);

        // Assert
        assertThat(applied).isFalse();
        assertThat(stock.getChangePercent()).isEqualByComparingTo("1.25");
        assertThat(stock.getChangeAmount()).isEqualByComparingTo("0.50");
    }

    @Test
    void applyFromPriorCloseIfMissing_computes_whenSourcePercentIsNull() {
        // Arrange
        Stock stock = baseStock();
        stock.setChangePercent(null);

        // Act
        boolean applied = ChangeFromCandlesUpdater.applyFromPriorCloseIfMissing(
                stock, new BigDecimal("110"), new BigDecimal("100"), 2);

        // Assert
        assertThat(applied).isTrue();
        assertThat(stock.getChangeAmount()).isEqualByComparingTo("10.00");
        assertThat(stock.getChangePercent()).isEqualByComparingTo("10.00");
    }

    @Test
    void applyFromPriorCloseIfMissing_computes_whenSourcePercentIsZero() {
        // Arrange
        Stock stock = baseStock();
        stock.setChangePercent(BigDecimal.ZERO);

        // Act
        boolean applied = ChangeFromCandlesUpdater.applyFromPriorCloseIfMissing(
                stock, new BigDecimal("95"), new BigDecimal("100"), 2);

        // Assert
        assertThat(applied).isTrue();
        assertThat(stock.getChangeAmount()).isEqualByComparingTo("-5.00");
        assertThat(stock.getChangePercent()).isEqualByComparingTo("-5.00");
    }

    @Test
    void applyFromPriorCloseIfMissing_skips_whenCurrentPriceIsNull() {
        // Arrange
        Stock stock = baseStock();
        stock.setChangePercent(null);

        // Act
        boolean applied = ChangeFromCandlesUpdater.applyFromPriorCloseIfMissing(
                stock, null, new BigDecimal("100"), 2);

        // Assert
        assertThat(applied).isFalse();
        assertThat(stock.getChangePercent()).isNull();
    }

    @Test
    void applyFromPriorCloseIfMissing_skips_whenPriorCloseIsNull() {
        // Arrange
        Stock stock = baseStock();
        stock.setChangePercent(null);

        // Act
        boolean applied = ChangeFromCandlesUpdater.applyFromPriorCloseIfMissing(
                stock, new BigDecimal("100"), null, 2);

        // Assert
        assertThat(applied).isFalse();
        assertThat(stock.getChangePercent()).isNull();
    }

    private static Stock baseStock() {
        Stock stock = new Stock();
        stock.setSymbol("THYAO");
        stock.setCurrentPrice(new BigDecimal("100"));
        return stock;
    }
}
