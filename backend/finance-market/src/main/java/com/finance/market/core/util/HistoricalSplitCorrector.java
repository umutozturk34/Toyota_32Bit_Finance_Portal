package com.finance.market.core.util;

import com.finance.market.core.dto.external.YahooCandleDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Corrects Yahoo Finance's broken historical split adjustments for BIST stocks whose
 * pre-2005 candle prices were divided by a factor that doesn't reconcile with their
 * post-2005 ledger (e.g. THYAO 2004-12-31 close 0.0010 vs 2005-01-03 open 1.0018 →
 * ~1000× discontinuity).
 *
 * <p>Detection: compares the latest pre-2005 close to the earliest post-2005 open
 * within the candle batch. If the ratio is anomalous (greater than {@link #SUSPICIOUS_RATIO_HIGH}
 * or less than {@link #SUSPICIOUS_RATIO_LOW}), multiplies all pre-2005 OHLC values by
 * the discrepancy so the series joins cleanly at the year boundary. Returns the batch
 * unchanged when the ratio is sane, when the batch does not straddle the boundary, or
 * when reference closes are missing.
 *
 * <p>Stateless and side-effect free; safe to call from any batch upsert pipeline.
 */
public final class HistoricalSplitCorrector {

    /**
     * Cutoff for the Turkish lira redenomination boundary. Yahoo's broken adjustments
     * always cluster at this exact transition.
     */
    static final LocalDateTime BOUNDARY = LocalDateTime.of(2005, 1, 3, 0, 0);

    /**
     * Ratio above which the post/pre jump is treated as a data-quality discontinuity
     * rather than a legitimate price move. 10× corresponds to a single-session move that
     * has never occurred on BIST outside corporate-action artifacts.
     */
    static final BigDecimal SUSPICIOUS_RATIO_HIGH = BigDecimal.valueOf(10);

    /**
     * Mirror of {@link #SUSPICIOUS_RATIO_HIGH} for the inverted direction (post much
     * smaller than pre). Same physical justification.
     */
    static final BigDecimal SUSPICIOUS_RATIO_LOW = new BigDecimal("0.1");

    private HistoricalSplitCorrector() {}

    /**
     * Returns a new list with OHLC values for pre-{@link #BOUNDARY} candles rescaled by
     * the detected discontinuity ratio, or the input list itself when no correction is
     * warranted. The input is never mutated.
     */
    public static List<YahooCandleDto> correct(List<YahooCandleDto> candles) {
        if (candles == null || candles.isEmpty()) return candles;

        YahooCandleDto lastPre = null;
        YahooCandleDto firstPost = null;
        for (YahooCandleDto c : candles) {
            if (c == null || c.candleDate() == null) continue;
            if (c.candleDate().isBefore(BOUNDARY)) {
                if (lastPre == null || c.candleDate().isAfter(lastPre.candleDate())) lastPre = c;
            } else {
                if (firstPost == null || c.candleDate().isBefore(firstPost.candleDate())) firstPost = c;
            }
        }
        if (lastPre == null || firstPost == null) return candles;
        if (lastPre.close() == null || firstPost.open() == null) return candles;
        if (lastPre.close().signum() == 0) return candles;

        BigDecimal ratio = firstPost.open().divide(lastPre.close(), 8, RoundingMode.HALF_UP);
        if (ratio.compareTo(SUSPICIOUS_RATIO_HIGH) <= 0 && ratio.compareTo(SUSPICIOUS_RATIO_LOW) >= 0) {
            return candles;
        }
        return candles.stream()
                .map(c -> c.candleDate() != null && c.candleDate().isBefore(BOUNDARY)
                        ? scale(c, ratio)
                        : c)
                .toList();
    }

    private static YahooCandleDto scale(YahooCandleDto c, BigDecimal ratio) {
        return new YahooCandleDto(
                c.candleDate(),
                mul(c.open(), ratio),
                mul(c.high(), ratio),
                mul(c.low(), ratio),
                mul(c.close(), ratio),
                c.volume()
        );
    }

    private static BigDecimal mul(BigDecimal v, BigDecimal ratio) {
        return v == null ? null : v.multiply(ratio);
    }
}
