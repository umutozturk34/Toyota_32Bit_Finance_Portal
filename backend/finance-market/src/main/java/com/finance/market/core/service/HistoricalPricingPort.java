package com.finance.market.core.service;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Outbound port for fetching an asset's historical TRY price series, consumed by portfolio
 * back-fill/valuation across module boundaries.
 */
public interface HistoricalPricingPort {

    /** TRY prices keyed by date over the inclusive {@code [from, to]} window. */
    Map<LocalDate, BigDecimal> getPriceSeries(MarketType type, String assetCode,
                                              LocalDate from, LocalDate to);
}
