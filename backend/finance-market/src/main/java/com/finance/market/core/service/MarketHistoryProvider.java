package com.finance.market.core.service;

import com.finance.common.model.CandlePeriod;
import com.finance.common.model.MarketType;

import java.time.LocalDate;
import java.util.List;

public interface MarketHistoryProvider {

    MarketType getMarketType();

    List<?> getHistory(String code, CandlePeriod period);

    List<?> getHistoryInRange(String code, LocalDate from, LocalDate to);
}
