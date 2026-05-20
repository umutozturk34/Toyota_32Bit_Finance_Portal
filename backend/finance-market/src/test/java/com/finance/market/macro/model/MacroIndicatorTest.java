package com.finance.market.macro.model;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MacroIndicatorTest {

    @Test
    void should_recordObservation_when_dateIsLaterThanCurrentLast() {
        MacroIndicator indicator = buildIndicator();
        indicator.recordObservation(LocalDate.of(2026, 5, 1), new BigDecimal("40.00"));

        indicator.recordObservation(LocalDate.of(2026, 5, 15), new BigDecimal("42.50"));

        assertThat(indicator.getLastDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(indicator.getLastValue()).isEqualByComparingTo("42.50");
    }

    @Test
    void should_ignoreObservation_when_dateIsOlderThanCurrentLast() {
        MacroIndicator indicator = buildIndicator();
        indicator.recordObservation(LocalDate.of(2026, 5, 15), new BigDecimal("42.50"));

        indicator.recordObservation(LocalDate.of(2026, 5, 1), new BigDecimal("40.00"));

        assertThat(indicator.getLastDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(indicator.getLastValue()).isEqualByComparingTo("42.50");
    }

    @Test
    void should_skipObservation_when_dateOrValueIsNull() {
        MacroIndicator indicator = buildIndicator();

        indicator.recordObservation(null, new BigDecimal("1"));
        indicator.recordObservation(LocalDate.of(2026, 5, 1), null);

        assertThat(indicator.getLastDate()).isNull();
        assertThat(indicator.getLastValue()).isNull();
    }

    @Test
    void should_reportStale_when_frequencyDetectsGap() {
        MacroIndicator indicator = buildIndicatorWithFrequency(MacroFrequency.WEEKLY);
        indicator.recordObservation(LocalDate.of(2026, 5, 10), new BigDecimal("50"));

        boolean stale = indicator.isStale(LocalDate.of(2026, 5, 20));

        assertThat(stale).isTrue();
    }

    @Test
    void should_overwriteMetadata_when_applyingNewDefinition() {
        MacroIndicator indicator = buildIndicator();

        indicator.applyDefinition("newLabel", MacroCategory.INFLATION, MacroUnit.INDEX,
                MacroFrequency.MONTHLY, "USD", DepositMaturity.M3, true);

        assertThat(indicator.getLabel()).isEqualTo("newLabel");
        assertThat(indicator.getCategory()).isEqualTo(MacroCategory.INFLATION);
        assertThat(indicator.getUnit()).isEqualTo(MacroUnit.INDEX);
        assertThat(indicator.getFrequency()).isEqualTo(MacroFrequency.MONTHLY);
        assertThat(indicator.getCurrency()).isEqualTo("USD");
        assertThat(indicator.getMaturity()).isEqualTo(DepositMaturity.M3);
        assertThat(indicator.isProminent()).isTrue();
    }

    private MacroIndicator buildIndicator() {
        return buildIndicatorWithFrequency(MacroFrequency.DAILY);
    }

    private MacroIndicator buildIndicatorWithFrequency(MacroFrequency frequency) {
        Instrument instrument = Instrument.create(MarketType.MACRO_RATE, "TP.TEST");
        return MacroIndicator.builder()
                .instrument(instrument)
                .code("TP.TEST")
                .label("test")
                .category(MacroCategory.RATES)
                .unit(MacroUnit.PERCENT)
                .frequency(frequency)
                .prominent(false)
                .build();
    }
}
