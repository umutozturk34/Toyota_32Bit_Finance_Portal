package com.finance.market.stock.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockCandleTest {

    @Test
    void should_beEqual_when_idsMatch() {
        StockCandle a = StockCandle.builder().id(7L).stockSymbol("THYAO").build();
        StockCandle b = StockCandle.builder().id(7L).stockSymbol("GARAN").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void should_beEqualToItself_when_comparedReflexively() {
        StockCandle candle = StockCandle.builder().id(1L).build();

        assertThat(candle).isEqualTo(candle);
    }

    @Test
    void should_notBeEqual_when_idsDiffer() {
        StockCandle a = StockCandle.builder().id(1L).build();
        StockCandle b = StockCandle.builder().id(2L).build();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void should_notBeEqual_when_eitherIdIsNull() {
        StockCandle persisted = StockCandle.builder().id(1L).build();
        StockCandle transientCandle = StockCandle.builder().build();

        assertThat(transientCandle).isNotEqualTo(persisted);
        assertThat(persisted).isNotEqualTo(transientCandle);
    }

    @Test
    void should_notBeEqual_when_comparedToDifferentType() {
        StockCandle candle = StockCandle.builder().id(1L).build();

        assertThat(candle).isNotEqualTo("not-a-candle");
    }
}
