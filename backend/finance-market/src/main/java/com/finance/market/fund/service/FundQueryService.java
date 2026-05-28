package com.finance.market.fund.service;
import com.finance.market.core.service.MarketHistoryProvider;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.fund.dto.response.FundCandleResponse;
import com.finance.market.fund.mapper.FundResponseMapper;
import com.finance.shared.model.CandlePeriod;
import com.finance.market.fund.model.FundCandle;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.fund.repository.FundCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Serves fund unit-price candle history for a tracked fund over a preset period or explicit range. */
@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundQueryService implements MarketHistoryProvider {

    private final FundCandleRepository fundCandleRepository;
    private final FundResponseMapper fundResponseMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;

    @Override
    public MarketType getMarketType() {
        return MarketType.FUND;
    }

    @Override
    public List<FundCandleResponse> getHistory(String fundCode, CandlePeriod period) {
        return loadCandles(fundCode, period.toStartDateTime(), LocalDateTime.now());
    }

    @Override
    public List<FundCandleResponse> getHistoryInRange(String fundCode, LocalDate from, LocalDate to) {
        return loadCandles(fundCode, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    private List<FundCandleResponse> loadCandles(String fundCode, LocalDateTime from, LocalDateTime to) {
        String normalizedCode = trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.FUND, fundCode);
        List<FundCandle> candles = fundCandleRepository
                .findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(normalizedCode, from, to);
        return fundResponseMapper.toFundCandleResponses(candles);
    }
}
