package com.finance.market.stock.service;
import com.finance.market.core.cache.MarketAssetCacheHelper;

import com.finance.market.core.service.TrackedAssetDataService;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.stock.model.Stock;
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
    public void validateExists(TrackedAssetUpsertCommand command) {
        String symbol = command.getAssetCode();
        if (!stockUpdateService.exists(symbol)) {
            throw new BusinessException("error.market.stockNotFound", symbol);
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
