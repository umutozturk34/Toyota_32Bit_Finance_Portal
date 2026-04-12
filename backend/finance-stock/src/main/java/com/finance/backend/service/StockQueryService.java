package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.mapper.StockResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.StockCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final StockCandleRepository stockCandleRepository;
    private final StockResponseMapper stockResponseMapper;
    private final TrackedAssetService trackedAssetService;

    public List<CandleResponse> getStockHistory(String symbol, CandlePeriod period) {
        String normalizedCode = trackedAssetService.resolveEnabledCodeOrThrow(TrackedAssetType.STOCK, symbol);
        if (period == CandlePeriod.ALL) {
            return stockResponseMapper.toStockCandleResponses(stockCacheService.getHistory(normalizedCode));
        }
        LocalDateTime start = period.toStartDateTime();
        List<StockCandle> candles = stockCandleRepository
                .findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(normalizedCode, start, LocalDateTime.now());
        return stockResponseMapper.toStockCandleResponses(candles);
    }
}
