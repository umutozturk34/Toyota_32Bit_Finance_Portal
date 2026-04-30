package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.mapper.CommodityResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityCandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
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
        String normalizedCode = trackedAssetQueryService.resolveEnabledCodeOrThrow(
                TrackedAssetType.COMMODITY, code);
        List<CommodityCandle> candles = commodityCandleRepository
                .findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                        normalizedCode, period.toStartDateTime(), LocalDateTime.now());
        return commodityResponseMapper.toCommodityCandleResponses(candles);
    }
}
