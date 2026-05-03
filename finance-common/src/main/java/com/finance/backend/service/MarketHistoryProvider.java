package com.finance.backend.service;

import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.MarketType;

import java.time.LocalDate;
import java.util.List;

public interface MarketHistoryProvider {

    MarketType getMarketType();

    List<?> getHistory(String code, CandlePeriod period);

    List<?> getHistoryInRange(String code, LocalDate from, LocalDate to);
}
