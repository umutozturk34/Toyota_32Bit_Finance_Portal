package com.finance.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CryptoSymbolResolver {

    private final TrackedAssetQueryService trackedAssetQueryService;

    public String resolveBinanceSymbol(String coinGeckoId) {
        if (coinGeckoId == null || coinGeckoId.isBlank()) {
            return null;
        }
        String normalizedId = coinGeckoId.trim().toLowerCase();
        return trackedAssetQueryService.getCryptoBinanceSymbol(normalizedId).orElse(null);
    }
}
