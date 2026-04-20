package com.finance.backend.constants;

import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.service.TrackedAssetQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MarketConstants {
    private final TrackedAssetQueryService trackedAssetQueryService;

    public List<String> getTrackedCryptos() {
        return trackedAssetQueryService.getEnabledCodes(TrackedAssetType.CRYPTO);
    }

    public List<String> getTrackedBistStocks() {
        return trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK);
    }

    public List<String> getTrackedFunds() {
        return trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND);
    }

    public String getBinanceSymbol(String coinGeckoId) {
        if (coinGeckoId == null || coinGeckoId.isBlank()) {
            return null;
        }

        String normalizedId = coinGeckoId.trim().toLowerCase();
        return trackedAssetQueryService.getCryptoBinanceSymbol(normalizedId)
                .orElse(null);
    }
}
