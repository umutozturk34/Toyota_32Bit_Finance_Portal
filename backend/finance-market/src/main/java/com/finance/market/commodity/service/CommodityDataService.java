package com.finance.market.commodity.service;
import com.finance.market.core.cache.MarketAssetCacheHelper;

import com.finance.market.core.service.TrackedAssetDataService;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.commodity.model.Commodity;
import com.finance.common.model.TrackedAssetType;
import com.finance.shared.util.CodeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Commodity {@link TrackedAssetDataService}: validates a code (known derivatives skip the upstream
 * check) before tracking, and delegates refresh/cache work.
 */
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
    public void validateExists(TrackedAssetUpsertCommand command) {
        String code = command.getAssetCode();
        String normalized = CodeNormalizer.upper(code);
        if (derivativeCalculator.isKnownDerivative(normalized)) return;
        if (!commodityUpdateService.exists(code)) {
            throw new BusinessException("error.market.commodityNotFound", code);
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
