package com.finance.backend.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceEventTypeTest {

    @ParameterizedTest
    @CsvSource({
            "BUY,  BUY",
            "SELL, SELL"
    })
    void fromTransactionSideMatchesCorrespondingEventType(TransactionSide side,
                                                          PerformanceEventType expected) {
        PerformanceEventType actual = PerformanceEventType.fromTransactionSide(side);

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(TransactionSide.class)
    void fromTransactionSideNeverReturnsNullForDefinedSides(TransactionSide side) {
        assertThat(PerformanceEventType.fromTransactionSide(side)).isNotNull();
    }
}
