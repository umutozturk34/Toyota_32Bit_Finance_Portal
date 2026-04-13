package com.finance.backend.service;

import com.finance.backend.client.YahooStockClient;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.StockSegment;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
public class StockSnapshotService {

    private final YahooStockClient yahooStockClient;
    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final TrackedAssetService trackedAssetService;
    private final MarketConstants marketConstants;
    private final TransactionTemplate transactionTemplate;

    public StockSnapshotService(YahooStockClient yahooStockClient,
                                StockMapper stockMapper,
                                StockRepository stockRepository,
                                MarketCacheService<Stock, StockCandle> stockCacheService,
                                TrackedAssetService trackedAssetService,
                                MarketConstants marketConstants,
                                PlatformTransactionManager transactionManager) {
        this.yahooStockClient = yahooStockClient;
        this.stockMapper = stockMapper;
        this.stockRepository = stockRepository;
        this.stockCacheService = stockCacheService;
        this.trackedAssetService = trackedAssetService;
        this.marketConstants = marketConstants;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean existsInApi(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalized.isBlank()) return false;
        try {
            YahooStockQuoteDto dto = yahooStockClient.fetchQuote(normalized);
            return dto != null && dto.currentPrice() != null;
        } catch (Exception e) {
            log.warn("Stock existence check failed for {}: {}", normalized, e.getMessage());
            return false;
        }
    }

    public void updateStockSnapshots() {
        List<String> bistStocks = marketConstants.getTrackedBistStocks();
        if (bistStocks.isEmpty()) {
            log.warn("No BIST stocks configured in environment variables");
            return;
        }
        log.info("Starting snapshot update for {} BIST stocks", bistStocks.size());

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                bistStocks,
                symbol -> {
                    Stock stock = transactionTemplate.execute(status -> updateSingleStockSnapshot(symbol));
                    stockCacheService.putSnapshot(symbol, stock);
                },
                java.util.function.Function.identity(),
                "snapshot",
                10,
                (symbol, e) -> log.error("Failed to update snapshot for {}: {}", symbol, e.getMessage(), e),
                null,
                null);

        BatchLogHelper.logSummary(log, "Stock snapshot update", result);
    }

    public void refreshTrackedStockSnapshot(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalized.isBlank()) {
            return;
        }
        Stock stock = transactionTemplate.execute(status -> updateSingleStockSnapshot(normalized));
        stockCacheService.putSnapshot(normalized, stock);
        log.info("Refreshed tracked stock snapshot for {}", normalized);
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
        return trackedAssetService.getTrackedAsset(TrackedAssetType.STOCK, symbol)
                .map(tracked -> tracked.getStockSegment() != null
                        ? tracked.getStockSegment()
                        : StockSegment.EQUITY)
                .orElse(StockSegment.EQUITY);
    }
}
