package com.finance.market.commodity.service;
import com.finance.cache.service.MarketAssetCacheHelper;

import com.finance.common.service.TrackedAssetDataService;

import com.finance.cache.service.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.market.commodity.model.Commodity;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.util.CodeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CommodityDataService implements TrackedAssetDataService {

    private final CommodityUpdateService commodityUpdateService;
    private final MarketCacheService<Commodity> commodityCacheService;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.COMMODITY;
    }

    @Override
    public void validateExists(String code) {
        String normalized = CodeNormalizer.upper(code);
        if (derivativeCalculator.isKnownDerivative(normalized)) return;
        if (!commodityUpdateService.exists(code)) {
            throw new BusinessException("Emtia bulunamadı: " + code, "ASSET_NOT_FOUND");
        }
    }

    @Override
    public void refresh(String code) {
        commodityUpdateService.refresh(code);
    }

    @Override
    public void refreshAll() {
        commodityUpdateService.refreshAll();
    }

    @Override
    public void clearCache(String code) {
        MarketAssetCacheHelper.clearIfValid(code, commodityCacheService, true, log, "commodity");
    }
}
