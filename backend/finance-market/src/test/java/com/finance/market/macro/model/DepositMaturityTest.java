package com.finance.market.macro.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DepositMaturityTest {

    @ParameterizedTest
    @CsvSource({
            "M1, 1M",
            "M3, 3M",
            "M6, 6M",
            "M12, 1Y",
            "M12_PLUS, 1Y+",
            "TOTAL, Total",
    })
    void tenorLabel_returnsHumanShortLabel(DepositMaturity maturity, String expected) {
        String label = maturity.tenorLabel();

        assertThat(label).isEqualTo(expected);
    }
}
