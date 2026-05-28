package com.finance.market.stock.service;
import com.finance.market.core.service.MarketEntityWriter;

import com.finance.market.core.service.TrackedAssetQueryService;



import com.finance.common.config.AppProperties;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.common.exception.BusinessException;
import com.finance.market.stock.mapper.StockMapper;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import com.finance.common.model.MarketType;
import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.market.stock.repository.StockRepository;
import com.finance.market.core.util.CandleBatchUpsertTemplate;
import com.finance.market.core.util.ChangeFromCandlesUpdater;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Persists stock snapshots and candles. Resolves each stock's segment from its tracked-asset config
 * (defaulting to EQUITY), validates inbound quotes, and back-fills change percent from candles only
 * when missing.
 */
@Log4j2
@Component
public class StockEntityWriter implements MarketEntityWriter {

    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockMapper stockMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final AssetRegistryService assetRegistry;
    private final int scale;

    public StockEntityWriter(StockRepository stockRepository,
                             StockCandleRepository stockCandleRepository,
                             StockMapper stockMapper,
                             TrackedAssetQueryService trackedAssetQueryService,
                             AssetRegistryService assetRegistry,
                             AppProperties appProperties) {
        this.stockRepository = stockRepository;
        this.stockCandleRepository = stockCandleRepository;
        this.stockMapper = stockMapper;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.assetRegistry = assetRegistry;
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
        toPersist.setAsset(assetRegistry.upsert(MarketType.STOCK, symbol));
        stockRepository.save(toPersist);
        return toPersist;
    }

    public Stock findExisting(String symbol) {
        return stockRepository.findById(symbol).orElse(null);
    }

    public boolean refreshChangePercentFromCandles(Stock stock) {
        List<StockCandle> top2 = stockCandleRepository
                .findTop2ByStockSymbolOrderByCandleDateDesc(stock.getSymbol());
        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                stock, stock.getCurrentPrice(), top2, scale);
        if (changed) stockRepository.save(stock);
        return changed;
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
