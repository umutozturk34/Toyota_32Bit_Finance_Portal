package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class StockDataService implements TrackedAssetDataService {

    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final StockSnapshotService stockSnapshotService;
    private final StockCandleService stockCandleService;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.STOCK;
    }

    @Override
    public void validateExists(String symbol) {
        if (!stockSnapshotService.existsInApi(symbol)) {
            throw new BusinessException(
                    "Hisse senedi bulunamadı: " + symbol, "ASSET_NOT_FOUND");
        }
    }

    public void updateStockSnapshots() {
        stockSnapshotService.updateStockSnapshots();
    }

    @Override
    public void refreshSnapshot(String symbol) {
        stockSnapshotService.refreshTrackedStockSnapshot(symbol);
    }

    @Override
    public void refreshCandles(String symbol) {
        stockCandleService.refreshTrackedStockCandles(symbol);
    }

    @Override
    public void clearCache(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalized.isBlank()) {
            return;
        }
        stockCacheService.clearCache(normalized);
        log.info("Cleared tracked stock cache for {}", normalized);
    }

    public void updateStockCandles() {
        stockCandleService.updateStockCandles();
    }
}
