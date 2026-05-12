package com.finance.market.forex.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketAssetCacheHelper;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.service.TrackedAssetDataService;
import com.finance.market.forex.model.Forex;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ForexDataService implements TrackedAssetDataService {

    private final MarketCacheService<Forex> forexCacheService;
    private final ForexUpdateService forexUpdateService;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.FOREX;
    }

    @Override
    public void validateExists(TrackedAssetUpsertCommand command) {
        String code = command.getAssetCode();
        if (!forexUpdateService.isActiveCurrency(code)) {
            throw new BusinessException("error.market.forexNotFound", code);
        }
    }

    @Override
    public void refresh(String currencyCode) {
        forexUpdateService.refresh(currencyCode);
    }

    @Override
    public void refreshAll() {
        forexUpdateService.refreshAll();
    }

    @Override
    public void clearCache(String currencyCode) {
        MarketAssetCacheHelper.clearIfValid(currencyCode, forexCacheService, true, log, "forex");
    }
}
