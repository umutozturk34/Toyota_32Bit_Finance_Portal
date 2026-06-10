package com.finance.market.macro.dto;

import java.math.BigDecimal;

/**
 * Derived inflation figures for an index-based indicator: the year-over-year and month-over-month
 * percentage change of its cumulative price index. Either field is {@code null} when the matching
 * earlier observation is unavailable, and {@link #EMPTY} represents an indicator with no derivable rate.
 *
 * @param yoyChangePct year-over-year change in percent, or {@code null}
 * @param momChangePct month-over-month change in percent, or {@code null}
 */
public record InflationRate(BigDecimal yoyChangePct, BigDecimal momChangePct) {

    /** Sentinel for indicators that carry no derivable inflation rate (non-inflation or non-index series). */
    public static final InflationRate EMPTY = new InflationRate(null, null);
}
