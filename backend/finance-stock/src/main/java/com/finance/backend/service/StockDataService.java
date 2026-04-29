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
    private final StockUpdateService stockUpdateService;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.STOCK;
    }

    @Override
    public void validateExists(String symbol) {
        if (!stockUpdateService.exists(symbol)) {
            throw new BusinessException(
                    "Hisse senedi bulunamadı: " + symbol, "ASSET_NOT_FOUND");
        }
    }

    @Override
    public void refresh(String symbol) {
        stockUpdateService.refresh(symbol);
    }

    @Override
    public void refreshAll() {
        stockUpdateService.refreshAll();
    }

    @Override
    public void clearCache(String symbol) {
        MarketAssetCacheHelper.clearIfValid(symbol, stockCacheService, true, log, "stock");
    }
}
