package com.finance.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Geometric (Fisher) excess of a return over a benchmark — the single source of truth for
 * "how much did it really beat the benchmark by". Inputs and result are percentages.
 *
 * <p>{@code realExcess% = ((1 + return/100) / (1 + benchmark/100) - 1) * 100}
 *
 * <p>This is the inflation-/benchmark-adjusted (purchasing-power) outperformance, NOT the naive
 * arithmetic point-difference {@code return - benchmark}. Both carry the same SIGN — so a beats/loses
 * verdict and a ranking against a single benchmark are identical — but the arithmetic form badly
 * overstates the MAGNITUDE for large returns: an asset up 6275% against CPI up 403% really beat
 * inflation by +1166% (purchasing power x12.7), not the arithmetic +5872 points (which reads as x58.7).
 *
 * <p>Consolidates the formula used by the inflation-beater excess (benchmark = the chosen benchmark's
 * return) and the scenario real return (benchmark = CPI growth). The portfolio real return implements
 * the same Fisher relationship value-side (a CPI-deflated capital base) for its multi-lot,
 * money-weighted case, which cannot collapse to a single (return, benchmark) pair.
 */
public final class ReturnMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int FACTOR_SCALE = 12;

    private ReturnMath() {
    }

    /**
     * Geometric excess of {@code returnPct} over {@code benchmarkPct}, rounded HALF_UP to {@code scale}
     * decimals. Returns {@code null} when either input is null or the benchmark factor
     * {@code (1 + benchmark/100)} is not strictly positive (benchmark &le; -100%, where the ratio is
     * undefined) — callers treat null as "not comparable".
     */
    public static BigDecimal realExcessPct(BigDecimal returnPct, BigDecimal benchmarkPct, int scale) {
        if (returnPct == null || benchmarkPct == null) {
            return null;
        }
        BigDecimal benchmarkFactor = BigDecimal.ONE.add(benchmarkPct.divide(HUNDRED, FACTOR_SCALE, RoundingMode.HALF_UP));
        if (benchmarkFactor.signum() <= 0) {
            return null;
        }
        BigDecimal returnFactor = BigDecimal.ONE.add(returnPct.divide(HUNDRED, FACTOR_SCALE, RoundingMode.HALF_UP));
        return returnFactor.divide(benchmarkFactor, FACTOR_SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED)
                .setScale(scale, RoundingMode.HALF_UP);
    }
}
