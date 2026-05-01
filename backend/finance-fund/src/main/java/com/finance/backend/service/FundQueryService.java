package com.finance.backend.service;

import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.mapper.FundResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundQueryService implements MarketHistoryProvider {

    private final MarketCacheService<Fund> fundCacheService;
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
        String normalizedCode = trackedAssetQueryService.resolveEnabledCodeOrThrow(TrackedAssetType.FUND, fundCode);
        List<FundCandle> candles = fundCandleRepository
                .findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(normalizedCode, from, to);
        return fundResponseMapper.toFundCandleResponses(candles);
    }
}
