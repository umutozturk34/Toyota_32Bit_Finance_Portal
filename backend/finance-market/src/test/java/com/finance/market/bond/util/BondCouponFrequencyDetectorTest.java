package com.finance.market.bond.util;

import com.finance.market.bond.model.BondRateHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BondCouponFrequencyDetector}: a floater's coupon period is read from the spacing of its
 * sharp ex-coupon price drops, ignoring small market noise, and is undetermined (0) for a flat/noisy/short
 * series. AAA throughout; series are synthetic sawtooths mirroring the real TLREF/auction shape.
 */
class BondCouponFrequencyDetectorTest {

    /** A sawtooth: over each {@code stepDays} period the price rises gently, then drops {@code dropPct} on the ex-date. */
    private List<BondRateHistory> sawtooth(int periods, int stepDays, double dropPct) {
        List<BondRateHistory> rows = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        double price = 100.0;
        for (int p = 0; p < periods; p++) {
            for (int d = 0; d < stepDays; d++) {
                price += dropPct / stepDays;       // gentle daily accretion, well under the drop threshold
                rows.add(row(date, price));
                date = date.plusDays(1);
            }
            price -= dropPct;                       // single-day ex-coupon drop
            rows.add(row(date, price));
            date = date.plusDays(1);
        }
        return rows;
    }

    private BondRateHistory row(LocalDate date, double price) {
        return BondRateHistory.builder().rateDate(date).price(BigDecimal.valueOf(price)).build();
    }

    @Test
    void shouldDetectQuarterly_fromNinetyDayDropSpacing() {
        // Arrange: 5 quarterly periods (~90 days apart) with a clear ~9% ex-coupon drop each.
        List<BondRateHistory> history = sawtooth(5, 90, 9.0);

        // Act + Assert: median drop gap ≈ 91 days → 3-month period.
        assertThat(BondCouponFrequencyDetector.detectStepMonths(history)).isEqualTo(3);
    }

    @Test
    void shouldDetectSemiAnnual_fromHalfYearDropSpacing() {
        // Arrange: 4 semi-annual periods (~182 days apart) with a clear ~12% ex-coupon drop each.
        List<BondRateHistory> history = sawtooth(4, 182, 12.0);

        // Act + Assert: median drop gap ≈ 183 days → 6-month period.
        assertThat(BondCouponFrequencyDetector.detectStepMonths(history)).isEqualTo(6);
    }

    @Test
    void shouldReturnZero_forFlatSeriesWithNoDrops() {
        // Arrange: a gently rising series with no sharp drop at all.
        List<BondRateHistory> history = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 400; i++) {
            history.add(row(date, 100.0 + i * 0.01));
            date = date.plusDays(1);
        }

        // Act + Assert
        assertThat(BondCouponFrequencyDetector.detectStepMonths(history)).isZero();
    }

    @Test
    void shouldIgnoreSmallMarketNoise_belowThreshold() {
        // Arrange: a series that wiggles ±2% day to day but never drops past the 4% coupon threshold.
        List<BondRateHistory> history = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 400; i++) {
            history.add(row(date, i % 2 == 0 ? 100.0 : 98.0)); // ±2% oscillation
            date = date.plusDays(1);
        }

        // Act + Assert: no move clears the threshold, so no cadence is read.
        assertThat(BondCouponFrequencyDetector.detectStepMonths(history)).isZero();
    }

    @Test
    void shouldReturnZero_forTooShortHistory() {
        // Act + Assert: a couple of points cannot establish a regular cadence.
        assertThat(BondCouponFrequencyDetector.detectStepMonths(sawtooth(1, 90, 9.0))).isZero();
    }
}
