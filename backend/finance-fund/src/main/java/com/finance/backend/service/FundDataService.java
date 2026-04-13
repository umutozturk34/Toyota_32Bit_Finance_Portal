package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FundDataService {

    private final MarketCacheService<com.finance.backend.model.Fund, com.finance.backend.model.FundCandle> fundCacheService;
    private final FundSnapshotService fundSnapshotService;
    private final FundCandleService fundCandleService;

    public void validateFundExists(String fundCode) {
        if (!fundSnapshotService.existsInApi(fundCode)) {
            throw new BusinessException(
                    "Fon bulunamadı: " + fundCode, "ASSET_NOT_FOUND");
        }
    }

    public void updateFundSnapshots() {
        fundSnapshotService.updateFundSnapshots();
    }

    public void refreshTrackedFundSnapshot(String fundCode) {
        fundSnapshotService.refreshTrackedFundSnapshot(fundCode);
    }

    public void refreshTrackedFundCandles(String fundCode) {
        fundCandleService.refreshTrackedFundCandles(fundCode);
    }

    public void clearTrackedFundCache(String fundCode) {
        String normalized = fundCode == null ? "" : fundCode.trim().toUpperCase();
        if (normalized.isBlank()) {
            return;
        }
        fundCacheService.clearCache(normalized);
        log.info("Cleared tracked fund cache for {}", normalized);
    }

    public void updateFundCandles() {
        fundCandleService.updateFundCandles();
    }
}
