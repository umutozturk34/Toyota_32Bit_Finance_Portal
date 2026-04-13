package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FundDataService {

    private final MarketCacheService<Fund, FundCandle> fundCacheService;
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
