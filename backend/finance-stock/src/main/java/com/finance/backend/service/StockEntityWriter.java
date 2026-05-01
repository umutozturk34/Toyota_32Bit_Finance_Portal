package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
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
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Log4j2
@Component
public class StockEntityWriter implements MarketEntityWriter {

    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockMapper stockMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final int scale;

    public StockEntityWriter(StockRepository stockRepository,
                             StockCandleRepository stockCandleRepository,
                             StockMapper stockMapper,
                             TrackedAssetQueryService trackedAssetQueryService,
                             AppProperties appProperties) {
        this.stockRepository = stockRepository;
        this.stockCandleRepository = stockCandleRepository;
        this.stockMapper = stockMapper;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.scale = appProperties.getScale();
    }

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

    public boolean refreshChangePercentFromCandles(Stock stock) {
        if (stock.getCurrentPrice() == null) return false;
        List<StockCandle> latest = stockCandleRepository
                .findTop2ByStockSymbolOrderByCandleDateDesc(stock.getSymbol());
        if (latest.size() < 2) return false;
        BigDecimal previousPrice = latest.get(1).getClose();
        BigDecimal oldPercent = stock.getChangePercent();
        stock.applyChange(stock.getCurrentPrice(), previousPrice, scale);
        if (Objects.equals(oldPercent, stock.getChangePercent())) return false;
        stockRepository.save(stock);
        return true;
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
