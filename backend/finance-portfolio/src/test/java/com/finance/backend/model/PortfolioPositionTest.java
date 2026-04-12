package com.finance.backend.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPositionTest {

    @Test
    void addQuantityRecalculatesWeightedAverageCost() {
        PortfolioPosition pos = buildPosition(
                new BigDecimal("2.00000000"), new BigDecimal("60000.0000"), new BigDecimal("120000.0000"));

        pos.addQuantity(new BigDecimal("1.00000000"), new BigDecimal("70000.0000"));

        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("3.00000000"));
        assertThat(pos.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("190000.0000"));
        assertThat(pos.getAverageCostTry()).isEqualByComparingTo(new BigDecimal("63333.3333"));
    }

    @Test
    void addQuantityFromZeroSetsAverageCostEqualToUnitPrice() {
        PortfolioPosition pos = buildPosition(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        pos.addQuantity(new BigDecimal("5.00000000"), new BigDecimal("250000.0000"));

        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("5.00000000"));
        assertThat(pos.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("250000.0000"));
        assertThat(pos.getAverageCostTry()).isEqualByComparingTo(new BigDecimal("50000.0000"));
    }

    @Test
    void addQuantitySmallFractionMaintainsPrecision() {
        PortfolioPosition pos = buildPosition(
                new BigDecimal("0.50000000"), new BigDecimal("65000.0000"), new BigDecimal("32500.0000"));

        pos.addQuantity(new BigDecimal("0.01538461"), new BigDecimal("1000.0000"));

        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("0.51538461"));
        assertThat(pos.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("33500.0000"));
        assertThat(pos.getQuantity().scale()).isEqualTo(8);
    }

    @Test
    void removeQuantityReducesCostProportionally() {
        PortfolioPosition pos = buildPosition(
                new BigDecimal("10.00000000"), new BigDecimal("40.0000"), new BigDecimal("400.0000"));

        pos.removeQuantity(new BigDecimal("5.00000000"));

        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("5.00000000"));
        assertThat(pos.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("200.0000"));
    }

    @Test
    void removeQuantityFullPositionZeroesOut() {
        PortfolioPosition pos = buildPosition(
                new BigDecimal("3.00000000"), new BigDecimal("100.0000"), new BigDecimal("300.0000"));

        pos.removeQuantity(new BigDecimal("3.00000000"));

        assertThat(pos.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pos.getTotalCostTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void removeQuantityTotalCostNeverGoesNegative() {
        PortfolioPosition pos = buildPosition(
                new BigDecimal("1.00000000"), new BigDecimal("50000.0000"), new BigDecimal("50000.0000"));

        pos.removeQuantity(new BigDecimal("1.00000001"));

        assertThat(pos.getTotalCostTry().signum()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void removeQuantityMaintainsScale() {
        PortfolioPosition pos = buildPosition(
                new BigDecimal("10.00000000"), new BigDecimal("38.1234"), new BigDecimal("381.2340"));

        pos.removeQuantity(new BigDecimal("3.00000000"));

        assertThat(pos.getQuantity().scale()).isEqualTo(8);
        assertThat(pos.getTotalCostTry().scale()).isEqualTo(4);
    }

    @Test
    void addQuantityMultipleBuysConvergesWeightedAverage() {
        PortfolioPosition pos = buildPosition(
                new BigDecimal("100.00000000"), new BigDecimal("45.0000"), new BigDecimal("4500.0000"));

        pos.addQuantity(new BigDecimal("100.00000000"), new BigDecimal("5500.0000"));

        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("200.00000000"));
        assertThat(pos.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(pos.getAverageCostTry()).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    private PortfolioPosition buildPosition(BigDecimal qty, BigDecimal avgCost, BigDecimal totalCost) {
        return PortfolioPosition.builder()
                .quantity(qty)
                .averageCostTry(avgCost)
                .totalCostTry(totalCost)
                .build();
    }
}
