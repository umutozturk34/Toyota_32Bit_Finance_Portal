package com.finance.market.macro.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MacroFrequencyTest {

    @ParameterizedTest
    @CsvSource({
            "DAILY,2026-05-18,2026-05-18,false",
            "DAILY,2026-05-17,2026-05-18,true",
            "DAILY,,2026-05-18,true",
            "WEEKLY,2026-05-11,2026-05-18,true",
            "WEEKLY,2026-05-15,2026-05-18,false",
            "MONTHLY,2026-04-30,2026-05-01,true",
            "MONTHLY,2026-05-01,2026-05-18,false",
            "MONTHLY,,2026-05-18,true"
    })
    void should_returnExpectedStaleFlag_when_evaluatingFrequencyAgainstDate(
            MacroFrequency frequency, String lastObservedRaw, String todayRaw, boolean expected) {
        LocalDate lastObserved = lastObservedRaw == null || lastObservedRaw.isBlank()
                ? null : LocalDate.parse(lastObservedRaw);
        LocalDate today = LocalDate.parse(todayRaw);

        boolean result = frequency.isStale(lastObserved, today);

        assertThat(result).isEqualTo(expected);
    }
}
