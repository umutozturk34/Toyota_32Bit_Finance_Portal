package com.finance.market.commodity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommodityCandleTest {

    @Test
    void should_beEqual_when_idsMatch() {
        CommodityCandle a = CommodityCandle.builder().id(7L).commodityCode("GC=F").build();
        CommodityCandle b = CommodityCandle.builder().id(7L).commodityCode("SI=F").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void should_beEqualToItself_when_comparedReflexively() {
        CommodityCandle candle = CommodityCandle.builder().id(1L).build();

        assertThat(candle).isEqualTo(candle);
    }

    @Test
    void should_notBeEqual_when_idsDiffer() {
        CommodityCandle a = CommodityCandle.builder().id(1L).build();
        CommodityCandle b = CommodityCandle.builder().id(2L).build();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void should_notBeEqual_when_eitherIdIsNull() {
        CommodityCandle persisted = CommodityCandle.builder().id(1L).build();
        CommodityCandle transientCandle = CommodityCandle.builder().build();

        assertThat(transientCandle).isNotEqualTo(persisted);
        assertThat(persisted).isNotEqualTo(transientCandle);
    }

    @Test
    void should_notBeEqual_when_comparedToDifferentType() {
        CommodityCandle candle = CommodityCandle.builder().id(1L).build();

        assertThat(candle).isNotEqualTo("not-a-candle");
    }
}
