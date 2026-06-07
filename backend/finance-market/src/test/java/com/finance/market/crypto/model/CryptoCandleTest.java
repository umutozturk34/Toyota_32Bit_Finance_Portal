package com.finance.market.crypto.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoCandleTest {

    @Test
    void should_beEqual_when_idsMatch() {
        CryptoCandle a = CryptoCandle.builder().id(7L).cryptoId("bitcoin").build();
        CryptoCandle b = CryptoCandle.builder().id(7L).cryptoId("ethereum").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void should_beEqualToItself_when_comparedReflexively() {
        CryptoCandle candle = CryptoCandle.builder().id(1L).build();

        assertThat(candle).isEqualTo(candle);
    }

    @Test
    void should_notBeEqual_when_idsDiffer() {
        CryptoCandle a = CryptoCandle.builder().id(1L).build();
        CryptoCandle b = CryptoCandle.builder().id(2L).build();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void should_notBeEqual_when_eitherIdIsNull() {
        CryptoCandle persisted = CryptoCandle.builder().id(1L).build();
        CryptoCandle transientCandle = CryptoCandle.builder().build();

        assertThat(transientCandle).isNotEqualTo(persisted);
        assertThat(persisted).isNotEqualTo(transientCandle);
    }

    @Test
    void should_notBeEqual_when_comparedToDifferentType() {
        CryptoCandle candle = CryptoCandle.builder().id(1L).build();

        assertThat(candle).isNotEqualTo("not-a-candle");
    }
}
