package com.finance.backend.service;

import com.finance.backend.client.YahooStockClient;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.StockSegment;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.ApiAssetValidator;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.MarketBatchRunner;
import com.finance.backend.util.TrackedRefreshRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Log4j2
@Service
public class StockSnapshotService implements SnapshotBatchRefresher {

    private final YahooStockClient yahooStockClient;
    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;

    public StockSnapshotService(YahooStockClient yahooStockClient,
                                StockMapper stockMapper,
                                StockRepository stockRepository,
                                MarketCacheService<Stock, StockCandle> stockCacheService,
                                TrackedAssetQueryService trackedAssetQueryService,
                                TransactionTemplate transactionTemplate) {
        this.yahooStockClient = yahooStockClient;
        this.stockMapper = stockMapper;
        this.stockRepository = stockRepository;
        this.stockCacheService = stockCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean existsInApi(String symbol) {
        return ApiAssetValidator.validate(symbol, true, sym -> {
            YahooStockQuoteDto dto = yahooStockClient.fetchQuote(sym);
            return dto != null && dto.currentPrice() != null;
        }, log, "Stock");
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.STOCK;
    }

    @Override
    public void refreshAll() {
        List<String> bistStocks = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK);
        if (bistStocks.isEmpty()) {
            log.warn("No BIST stocks configured in environment variables");
            return;
        }
        log.info("Starting snapshot update for {} BIST stocks", bistStocks.size());

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                bistStocks,
                symbol -> {
                    Stock stock = transactionTemplate.execute(status -> updateSingleStockSnapshot(symbol));
                    stockCacheService.putSnapshot(symbol, stock);
                },
                Function.identity(),
                log, "Stock", "snapshot", 10);

        BatchLogHelper.logSummary(log, "Stock snapshot update", result);
    }

    @Override
    public void refreshSnapshot(String symbol) {
        TrackedRefreshRunner.refreshSnapshot(symbol, CodeNormalizer::upper, normalized -> {
            Stock stock = transactionTemplate.execute(status -> updateSingleStockSnapshot(normalized));
            stockCacheService.putSnapshot(normalized, stock);
            return true;
        }, log, "stock");
    }

    private Stock updateSingleStockSnapshot(String symbol) {
        YahooStockQuoteDto dto = yahooStockClient.fetchQuote(symbol);
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
        Stock stock = stockRepository.findById(symbol).orElse(null);
        Stock toPersist;
        if (stock != null) {
            stockMapper.updateEntityFromDto(stock, dto, now);
            toPersist = stock;
        } else {
            toPersist = stockMapper.toEntity(dto, now);
        }
        toPersist.setStockSegment(resolveStockSegment(symbol));
        stockRepository.save(toPersist);
        return toPersist;
    }

    private StockSegment resolveStockSegment(String symbol) {
        return trackedAssetQueryService.getTrackedAsset(TrackedAssetType.STOCK, symbol)
                .map(tracked -> tracked.getStockSegment() != null
                        ? tracked.getStockSegment()
                        : StockSegment.EQUITY)
                .orElse(StockSegment.EQUITY);
    }
}
