package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FundDataService implements TrackedAssetDataService {

    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final FundSnapshotService fundSnapshotService;
    private final FundCandleService fundCandleService;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.FUND;
    }

    @Override
    public void validateExists(String fundCode) {
        if (!fundSnapshotService.existsInApi(fundCode)) {
            throw new BusinessException(
                    "Fon bulunamadı: " + fundCode, "ASSET_NOT_FOUND");
        }
    }

    @Override
    public void refresh(String fundCode) {
        fundSnapshotService.refreshSnapshot(fundCode);
        fundCandleService.refreshCandles(fundCode);
    }

    @Override
    public void refreshAll() {
        fundSnapshotService.refreshAll();
        fundCandleService.refreshAll();
    }

    @Override
    public void clearCache(String fundCode) {
        MarketAssetCacheHelper.clearIfValid(fundCode, fundCacheService, true, log, "fund");
    }
}
