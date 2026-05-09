package com.finance.market.commodity.service;
import com.finance.market.core.service.MarketHistoryProvider;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.commodity.mapper.CommodityResponseMapper;
import com.finance.common.model.CandlePeriod;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommodityQueryService implements MarketHistoryProvider {

    private final CommodityCandleRepository commodityCandleRepository;
    private final CommodityResponseMapper commodityResponseMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;

    @Override
    public MarketType getMarketType() {
        return MarketType.COMMODITY;
    }

    @Override
    public List<CandleResponse> getHistory(String code, CandlePeriod period) {
        return loadCandles(code, period.toStartDateTime(), LocalDateTime.now());
    }

    @Override
    public List<CandleResponse> getHistoryInRange(String code, LocalDate from, LocalDate to) {
        return loadCandles(code, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    private List<CandleResponse> loadCandles(String code, LocalDateTime from, LocalDateTime to) {
        String normalizedCode = trackedAssetQueryService.resolveCodeOrThrow(
                TrackedAssetType.COMMODITY, code);
        List<CommodityCandle> candles = commodityCandleRepository
                .findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(normalizedCode, from, to);
        return commodityResponseMapper.toCommodityCandleResponses(candles);
    }
}
