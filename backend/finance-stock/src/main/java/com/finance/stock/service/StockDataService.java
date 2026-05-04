package com.finance.stock.service;
import com.finance.cache.service.MarketAssetCacheHelper;

import com.finance.common.service.TrackedAssetDataService;

import com.finance.cache.service.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.stock.model.Stock;
import com.finance.stock.model.StockCandle;
import com.finance.common.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class StockDataService implements TrackedAssetDataService {

    private final MarketCacheService<Stock> stockCacheService;
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
