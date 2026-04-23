package com.finance.backend.service;

import com.finance.backend.util.CodeNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CryptoSymbolResolver {

    private final TrackedAssetQueryService trackedAssetQueryService;

    public String resolveBinanceSymbol(String coinGeckoId) {
        String normalizedId = CodeNormalizer.lower(coinGeckoId);
        if (normalizedId.isBlank()) {
            return null;
        }
        return trackedAssetQueryService.getCryptoBinanceSymbol(normalizedId).orElse(null);
    }
}
