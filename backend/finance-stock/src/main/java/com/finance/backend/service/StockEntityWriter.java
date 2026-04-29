package com.finance.backend.service;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.StockSegment;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.util.CandleBatchUpsertTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class StockEntityWriter {

    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockMapper stockMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;

    public Stock saveSnapshot(YahooStockQuoteDto dto, String symbol) {
        if (dto == null) {
            throw new BusinessException(
                    "Failed to fetch stock data from external API: " + symbol,
                    "EXTERNAL_API_ERROR");
        }
        if (dto.currentPrice() == null) {
            throw new BusinessException(
                    "Invalid stock data received - missing price for: " + symbol,
                    "INVALID_EXTERNAL_DATA");
        }
        LocalDateTime now = LocalDateTime.now();
        Stock existing = stockRepository.findById(symbol).orElse(null);
        Stock toPersist;
        if (existing != null) {
            stockMapper.updateEntityFromDto(existing, dto, now);
            toPersist = existing;
        } else {
            toPersist = stockMapper.toEntity(dto, now);
        }
        toPersist.setStockSegment(resolveStockSegment(symbol));
        stockRepository.save(toPersist);
        return toPersist;
    }

    public int upsertCandles(String symbol, Stock stock, List<YahooCandleDto> candleDtos) {
        CandleBatchUpsertTemplate.UpsertResult<StockCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                candleDtos,
                dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                keys -> stockCandleRepository.findByStockSymbolAndCandleDateIn(symbol, keys),
                candle -> candle.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                stockMapper::updateCandleEntity,
                dto -> stockMapper.toCandleEntity(dto, stock));

        if (!upsertResult.newEntities().isEmpty()) {
            stockCandleRepository.saveAll(upsertResult.newEntities());
        }
        if (upsertResult.insertCount() > 0 || upsertResult.updateCount() > 0) {
            log.debug("{} - {} new, {} updated", symbol, upsertResult.insertCount(), upsertResult.updateCount());
        }
        return upsertResult.totalChanged();
    }

    private StockSegment resolveStockSegment(String symbol) {
        return trackedAssetQueryService.getTrackedAsset(TrackedAssetType.STOCK, symbol)
                .map(tracked -> tracked.getStockSegment() != null
                        ? tracked.getStockSegment()
                        : StockSegment.EQUITY)
                .orElse(StockSegment.EQUITY);
    }
}
