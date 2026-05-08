package com.finance.market.fund.service;
import com.finance.cache.service.MarketAssetCacheHelper;

import com.finance.common.service.TrackedAssetDataService;

import com.finance.cache.service.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import com.finance.common.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FundDataService implements TrackedAssetDataService {

    private final MarketCacheService<Fund> fundCacheService;
    private final FundUpdateService fundUpdateService;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.FUND;
    }

    @Override
    public void validateExists(String fundCode) {
        if (!fundUpdateService.exists(fundCode)) {
            throw new BusinessException(
                    "Fon bulunamadı: " + fundCode, "ASSET_NOT_FOUND");
        }
    }

    @Override
    public void refresh(String fundCode) {
        fundUpdateService.refresh(fundCode);
    }

    @Override
    public void refreshAll() {
        fundUpdateService.refreshAll();
    }

    @Override
    public void clearCache(String fundCode) {
        MarketAssetCacheHelper.clearIfValid(fundCode, fundCacheService, true, log, "fund");
    }
}
