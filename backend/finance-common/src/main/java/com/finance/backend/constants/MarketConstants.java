package com.finance.backend.constants;

import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.service.TrackedAssetService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MarketConstants {
    private final TrackedAssetService trackedAssetService;

    public List<String> getTrackedCryptos() {
        return trackedAssetService.getEnabledCodes(TrackedAssetType.CRYPTO);
    }

    public List<String> getTrackedBistStocks() {
        return trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK);
    }

    public List<String> getTrackedFunds() {
        return trackedAssetService.getEnabledCodes(TrackedAssetType.FUND);
    }

    public String getBinanceSymbol(String coinGeckoId) {
        if (coinGeckoId == null || coinGeckoId.isBlank()) {
            return null;
        }

        String normalizedId = coinGeckoId.trim().toLowerCase();
        return trackedAssetService.getCryptoBinanceSymbol(normalizedId)
                .orElse(null);
    }
}
