package com.finance.market.stock.service;
import com.finance.market.core.service.MarketHistoryProvider;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.stock.mapper.StockResponseMapper;
import com.finance.shared.model.CandlePeriod;
import com.finance.common.model.MarketType;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.stock.repository.StockCandleRepository;
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
public class StockQueryService implements MarketHistoryProvider {

    private final MarketCacheService<Stock> stockCacheService;
    private final StockCandleRepository stockCandleRepository;
    private final StockResponseMapper stockResponseMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;

    @Override
    public MarketType getMarketType() {
        return MarketType.STOCK;
    }

    @Override
    public List<CandleResponse> getHistory(String symbol, CandlePeriod period) {
        return loadCandles(symbol, period.toStartDateTime(), LocalDateTime.now());
    }

    @Override
    public List<CandleResponse> getHistoryInRange(String symbol, LocalDate from, LocalDate to) {
        return loadCandles(symbol, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    private List<CandleResponse> loadCandles(String symbol, LocalDateTime from, LocalDateTime to) {
        String normalizedCode = trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.STOCK, symbol);
        List<StockCandle> candles = stockCandleRepository
                .findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(normalizedCode, from, to);
        return stockResponseMapper.toStockCandleResponses(candles);
    }
}
