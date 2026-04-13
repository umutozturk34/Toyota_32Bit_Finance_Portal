package com.finance.backend.service;

import com.finance.backend.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class TrackedAssetRefreshService {

    private final CryptoDataService marketDataService;
    private final StockDataService stockDataService;
    private final FundDataService fundDataService;

    public void validateAssetExists(TrackedAssetType type, String code) {
        switch (type) {
            case CRYPTO -> marketDataService.validateCryptoExists(code);
            case STOCK -> stockDataService.validateStockExists(code);
            case FUND -> fundDataService.validateFundExists(code);
        }
    }

    @Async("taskExecutor")
    public void refreshAsync(TrackedAssetType type, String code) {
        log.info("Async refresh started for {} / {}", type, code);
        try {
            switch (type) {
                case CRYPTO -> {
                    marketDataService.refreshTrackedCryptoSnapshot(code);
                    marketDataService.refreshTrackedCryptoCandles(code);
                }
                case STOCK -> {
                    stockDataService.refreshTrackedStockSnapshot(code);
                    stockDataService.refreshTrackedStockCandles(code);
                }
                case FUND -> {
                    fundDataService.refreshTrackedFundSnapshot(code);
                    fundDataService.refreshTrackedFundCandles(code);
                }
            }
            log.info("Async refresh completed for {} / {}", type, code);
        } catch (Exception ex) {
            log.warn("Async refresh failed for {} / {}: {}", type, code, ex.getMessage());
        }
    }

    @Async("taskExecutor")
    public void clearCacheAsync(TrackedAssetType type, String code) {
        try {
            switch (type) {
                case CRYPTO -> marketDataService.clearTrackedCryptoCache(code);
                case STOCK -> stockDataService.clearTrackedStockCache(code);
                case FUND -> fundDataService.clearTrackedFundCache(code);
            }
        } catch (Exception ex) {
            log.warn("Async cache clear failed for {} / {}: {}", type, code, ex.getMessage());
        }
    }
}
