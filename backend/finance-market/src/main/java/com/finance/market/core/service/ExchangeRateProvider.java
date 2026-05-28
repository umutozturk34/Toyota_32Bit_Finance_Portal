package com.finance.market.core.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Supplies USD/TRY spot and history used to cross foreign-quoted prices into TRY.
 */
public interface ExchangeRateProvider {

    /** Current and previous USD/TRY rate; see {@link ExchangeRateSnapshot#isAvailable()}. */
    ExchangeRateSnapshot getCurrentUsdTry();

    /** Historical USD/TRY keyed by ISO date string ({@code yyyy-MM-dd}). */
    Map<String, BigDecimal> getUsdTryHistory();
}
