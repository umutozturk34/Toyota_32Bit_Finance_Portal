package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CommodityDataService implements TrackedAssetDataService {

    private final CommoditySnapshotService commoditySnapshotService;
    private final CommodityCandleService commodityCandleService;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.COMMODITY;
    }

    @Override
    public void validateExists(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (derivativeCalculator.isKnownDerivative(normalized)) return;
        if (!commoditySnapshotService.existsInApi(code)) {
            throw new BusinessException("Emtia bulunamadı: " + code, "ASSET_NOT_FOUND");
        }
    }

    @Override
    public void refreshSnapshot(String code) {
        commoditySnapshotService.refreshTrackedCommoditySnapshot(code);
    }

    @Override
    public void refreshCandles(String code) {
        commodityCandleService.refreshTrackedCommodityCandles(code);
    }

    @Override
    public void clearCache(String code) {
        MarketAssetCacheHelper.clearIfValid(code, commodityCacheService, true, log, "commodity");
    }

    public void updateCommoditySnapshots() {
        commoditySnapshotService.refreshAll();
    }

    public void updateCommodityCandles() {
        commodityCandleService.refreshAll();
    }
}
