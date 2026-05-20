package com.finance.market.macro.mapper;

import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.macro.dto.internal.MacroObservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsMacroMapperTest {

    private EvdsMacroMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EvdsMacroMapper();
    }

    @Test
    void should_returnEmpty_when_responseIsNull() {
        List<MacroObservation> result = mapper.extract(null, "TP.SOMETHING");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEmpty_when_responseHasNoItems() {
        EvdsDataResponse response = new EvdsDataResponse(0, null);

        List<MacroObservation> result = mapper.extract(response, "TP.SOMETHING");

        assertThat(result).isEmpty();
    }

    @Test
    void should_parseDailyAndMonthlyAndValueRows_when_extractingObservations() {
        EvdsDataResponse response = new EvdsDataResponse(3, List.of(
                Map.of("Tarih", "1-5-2026", "TP_BISTTLREF_ORAN", "49.85"),
                Map.of("Tarih", "2026-4", "TP_BISTTLREF_ORAN", "50.10"),
                Map.of("Tarih", "3-5-2026", "TP_BISTTLREF_ORAN", "")));

        List<MacroObservation> result = mapper.extract(response, "TP.BISTTLREF.ORAN");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).value()).isEqualByComparingTo("49.85");
        assertThat(result.get(1).value()).isEqualByComparingTo("50.10");
    }

    @Test
    void should_skipRow_when_valueIsUnparseable() {
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "1-5-2026", "TP_TEST", "abc")));

        List<MacroObservation> result = mapper.extract(response, "TP.TEST");

        assertThat(result).isEmpty();
    }
}
