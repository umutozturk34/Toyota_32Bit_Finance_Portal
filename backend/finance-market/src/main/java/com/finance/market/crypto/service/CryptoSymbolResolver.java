package com.finance.market.crypto.service;
import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.shared.util.CodeNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Maps a CoinGecko coin id to its configured Binance trading symbol for kline fetches. */
@Component
@RequiredArgsConstructor
public class CryptoSymbolResolver {

    private final TrackedAssetQueryService trackedAssetQueryService;

    /** Binance symbol for the coin id, or {@code null} when blank or unmapped. */
    public String resolveBinanceSymbol(String coinGeckoId) {
        String normalizedId = CodeNormalizer.lower(coinGeckoId);
        if (normalizedId.isBlank()) {
            return null;
        }
        return trackedAssetQueryService.getCryptoBinanceSymbol(normalizedId).orElse(null);
    }
}
