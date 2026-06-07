package com.finance.market.forex.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForexCandleTest {

    @Test
    void should_beEqual_when_idsMatch() {
        ForexCandle a = ForexCandle.builder().id(7L).currencyCode("USD").build();
        ForexCandle b = ForexCandle.builder().id(7L).currencyCode("EUR").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void should_beEqualToItself_when_comparedReflexively() {
        ForexCandle candle = ForexCandle.builder().id(1L).build();

        assertThat(candle).isEqualTo(candle);
    }

    @Test
    void should_notBeEqual_when_idsDiffer() {
        ForexCandle a = ForexCandle.builder().id(1L).build();
        ForexCandle b = ForexCandle.builder().id(2L).build();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void should_notBeEqual_when_eitherIdIsNull() {
        ForexCandle persisted = ForexCandle.builder().id(1L).build();
        ForexCandle transientCandle = ForexCandle.builder().build();

        assertThat(transientCandle).isNotEqualTo(persisted);
        assertThat(persisted).isNotEqualTo(transientCandle);
    }

    @Test
    void should_notBeEqual_when_comparedToDifferentType() {
        ForexCandle candle = ForexCandle.builder().id(1L).build();

        assertThat(candle).isNotEqualTo("not-a-candle");
    }
}
