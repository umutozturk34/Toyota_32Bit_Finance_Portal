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
import java.math.BigDecimal;
import com.finance.common.model.MarketType;
import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.market.stock.repository.StockRepository;
import com.finance.market.core.util.CandleBatchUpsertTemplate;
import com.finance.market.core.util.ChangeFromCandlesUpdater;
import com.finance.market.core.util.HistoricalSplitCorrector;
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

    /**
     * Upserts the stock by symbol (update in place when it exists), resolving its segment from tracked config.
     *
     * @throws BusinessException if {@code dto} is null (upstream failure) or carries no price
     */
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

    /** Stored stock for the symbol, or {@code null} if not yet persisted. */
    public Stock findExisting(String symbol) {
        return stockRepository.findById(symbol).orElse(null);
    }

    /**
     * Back-fills daily change percent from the two most recent candles, but only when it is still unset.
     *
     * @return whether change percent was computed and saved
     */
    public boolean refreshChangePercentFromCandles(Stock stock) {
        BigDecimal priorClose = stockCandleRepository
                .findFirstByStockSymbolOrderByCandleDateDesc(stock.getSymbol())
                .flatMap(latest -> stockCandleRepository
                        .findFirstByStockSymbolAndCandleDateBeforeOrderByCandleDateDesc(
                                stock.getSymbol(), latest.getCandleDate()))
                .map(StockCandle::getClose)
                .orElse(null);
        boolean changed = ChangeFromCandlesUpdater.applyFromPriorCloseIfMissing(
                stock, stock.getCurrentPrice(), priorClose, scale);
        if (changed) stockRepository.save(stock);
        return changed;
    }

    /**
     * Idempotently upserts daily candles after applying split correction to the historical series.
     *
     * @return the number of candles inserted or updated
     */
    public int upsertCandles(String symbol, Stock stock, List<YahooCandleDto> candleDtos) {
        List<YahooCandleDto> corrected = HistoricalSplitCorrector.correct(candleDtos);
        CandleBatchUpsertTemplate.UpsertResult<StockCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                corrected,
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
