package com.finance.market.core.service;

import com.finance.shared.model.CandlePeriod;
import com.finance.common.model.MarketType;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-side facade returning an asset's NATIVE-currency candle history (no TRY conversion), keyed
 * by market via {@link #getMarketType()}.
 */
public interface MarketHistoryProvider {

    /** Market this provider serves. */
    MarketType getMarketType();

    /** Candles covering the given preset period; element type is the market's candle response. */
    List<?> getHistory(String code, CandlePeriod period);

    /** Candles within the explicit inclusive date range. */
    List<?> getHistoryInRange(String code, LocalDate from, LocalDate to);
}
