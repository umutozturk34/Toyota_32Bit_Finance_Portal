package com.finance.common.service;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface HistoricalPricingPort {

    Map<LocalDate, BigDecimal> getPriceSeries(MarketType type, String assetCode,
                                              LocalDate from, LocalDate to);
}
