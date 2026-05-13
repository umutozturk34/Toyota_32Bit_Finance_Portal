package com.finance.market.fund.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FundCandleTest {

    private FundCandle candle(BigDecimal price, BigDecimal bulletin, BigDecimal shares,
                              BigDecimal investors, BigDecimal portfolio) {
        return FundCandle.builder()
                .price(price)
                .bulletinPrice(bulletin)
                .shareCount(shares)
                .investorCount(investors)
                .portfolioSize(portfolio)
                .build();
    }

    @Test
    void applyScaling_appliesBulletinScale_forByfFund() {
        FundCandle c = candle(new BigDecimal("12.345678"), new BigDecimal("1.23456"),
                new BigDecimal("100.123"), new BigDecimal("50.5"), new BigDecimal("1000.5"));

        c.applyScaling(FundType.BYF);

        assertThat(c.getBulletinPrice()).isEqualByComparingTo("1.2346");
        assertThat(c.getInvestorCount()).isNull();
    }

    @Test
    void applyScaling_clearsBulletin_forYatFund_andPreservesInvestorCount() {
        FundCandle c = candle(new BigDecimal("12.345678"), new BigDecimal("1.23456"),
                new BigDecimal("100.123"), new BigDecimal("50.5"), new BigDecimal("1000.5"));

        c.applyScaling(FundType.YAT);

        assertThat(c.getBulletinPrice()).isNull();
        assertThat(c.getInvestorCount()).isEqualByComparingTo("50.50");
    }

    @Test
    void applyScaling_skipsNullFields() {
        FundCandle c = candle(null, null, null, null, null);

        c.applyScaling(FundType.YAT);

        assertThat(c.getPrice()).isNull();
        assertThat(c.getShareCount()).isNull();
    }

    @Test
    void scaleFields_appliesPassedScale_toBulletinPriceOnly() {
        FundCandle c = candle(new BigDecimal("12.345678"), new BigDecimal("1.23456"),
                new BigDecimal("100.123"), new BigDecimal("50.5"), new BigDecimal("1000.5"));

        c.scaleFields(2);

        assertThat(c.getPrice().scale()).isEqualTo(6);
        assertThat(c.getBulletinPrice().scale()).isEqualTo(2);
        assertThat(c.getShareCount().scale()).isEqualTo(2);
    }

    @Test
    void equals_returnsTrue_whenIdsMatch() {
        FundCandle a = FundCandle.builder().id(1L).build();
        FundCandle b = FundCandle.builder().id(1L).build();

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void equals_returnsFalse_whenIdsDiffer() {
        FundCandle a = FundCandle.builder().id(1L).build();
        FundCandle b = FundCandle.builder().id(2L).build();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_returnsFalse_whenIdNull() {
        FundCandle a = FundCandle.builder().build();
        FundCandle b = FundCandle.builder().build();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_returnsFalse_whenComparedToDifferentType() {
        FundCandle a = FundCandle.builder().id(1L).build();

        assertThat(a).isNotEqualTo("not a candle");
    }
}
