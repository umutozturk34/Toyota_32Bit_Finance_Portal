package com.finance.portfolio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DailyDeltaTest {

    @Test
    void should_storeAmountAndPercent_when_constructedWithValues() {
        DailyDelta delta = new DailyDelta(new BigDecimal("12.50"), new BigDecimal("1.25"));

        assertThat(delta.amount()).isEqualByComparingTo("12.50");
        assertThat(delta.percent()).isEqualByComparingTo("1.25");
    }

    @Test
    void should_returnBothNull_when_emptyConstantIsUsed() {
        DailyDelta delta = DailyDelta.EMPTY;

        assertThat(delta.amount()).isNull();
        assertThat(delta.percent()).isNull();
    }

    @Test
    void should_allowNullFields_when_constructedExplicitlyWithNulls() {
        DailyDelta delta = new DailyDelta(null, null);

        assertThat(delta.amount()).isNull();
        assertThat(delta.percent()).isNull();
    }

    @Test
    void should_implementRecordEquality_when_sameFieldsAreUsed() {
        DailyDelta a = new DailyDelta(new BigDecimal("1"), new BigDecimal("2"));
        DailyDelta b = new DailyDelta(new BigDecimal("1"), new BigDecimal("2"));

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void should_supportNegativeValues_when_constructed() {
        DailyDelta delta = new DailyDelta(new BigDecimal("-200"), new BigDecimal("-2.5"));

        assertThat(delta.amount()).isEqualByComparingTo("-200");
        assertThat(delta.percent()).isEqualByComparingTo("-2.5");
    }
}
