package com.finance.market.viop.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ViopCandleTest {

    @Test
    void should_buildCandle_when_allFieldsProvided() {
        ViopCandle candle = ViopCandle.builder()
                .symbol("F_USDTRY0626")
                .candleDate(LocalDateTime.of(2026, 5, 1, 18, 0))
                .close(new BigDecimal("35.50"))
                .build();

        assertThat(candle.getSymbol()).isEqualTo("F_USDTRY0626");
        assertThat(candle.getCandleDate()).isEqualTo(LocalDateTime.of(2026, 5, 1, 18, 0));
        assertThat(candle.getClose()).isEqualByComparingTo("35.50");
    }

    @Test
    void should_allowSettingFieldsViaSetters_when_modifyingExistingCandle() {
        ViopCandle candle = ViopCandle.builder().symbol("F_X").close(new BigDecimal("1")).build();

        candle.setSymbol("F_Y");
        candle.setClose(new BigDecimal("2.5"));
        candle.setCandleDate(LocalDateTime.of(2026, 5, 2, 18, 0));

        assertThat(candle.getSymbol()).isEqualTo("F_Y");
        assertThat(candle.getClose()).isEqualByComparingTo("2.5");
        assertThat(candle.getCandleDate()).isEqualTo(LocalDateTime.of(2026, 5, 2, 18, 0));
    }

    @Test
    void should_setIdAndTimestamps_whenAssignedExplicitly() {
        ViopCandle candle = new ViopCandle();
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 12, 0);

        candle.setId(42L);
        candle.setCreatedAt(now);
        candle.setUpdatedAt(now);

        assertThat(candle.getId()).isEqualTo(42L);
        assertThat(candle.getCreatedAt()).isEqualTo(now);
        assertThat(candle.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void should_beEqualAndShareHash_when_idsMatch() {
        ViopCandle a = ViopCandle.builder().id(7L).symbol("F_X").build();
        ViopCandle b = ViopCandle.builder().id(7L).symbol("F_Y").build();

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void should_notBeEqual_when_idsDifferOrAreNullOrTypeDiffers() {
        ViopCandle persisted = ViopCandle.builder().id(1L).build();
        ViopCandle other = ViopCandle.builder().id(2L).build();
        ViopCandle transientCandle = ViopCandle.builder().build();

        assertThat(persisted).isNotEqualTo(other);
        assertThat(transientCandle).isNotEqualTo(persisted);
        assertThat(persisted).isNotEqualTo("not-a-candle");
    }
}
