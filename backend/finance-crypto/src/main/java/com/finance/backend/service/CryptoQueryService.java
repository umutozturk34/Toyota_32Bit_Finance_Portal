package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.mapper.CryptoResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CryptoCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class CryptoQueryService {

    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final CryptoResponseMapper cryptoResponseMapper;
    private final TrackedAssetService trackedAssetService;

    public List<CandleResponse> getCryptoHistory(String id, CandlePeriod period) {
        String normalizedCode = trackedAssetService.resolveEnabledCodeOrThrow(TrackedAssetType.CRYPTO, id);
        if (period == CandlePeriod.ALL) {
            return cryptoResponseMapper.toCryptoCandleResponses(cryptoCacheService.getHistory(normalizedCode));
        }
        LocalDateTime start = period.toStartDateTime();
        List<CryptoCandle> candles = cryptoCandleRepository
                .findByCryptoIdAndCandleDateBetweenOrderByCandleDateAsc(normalizedCode, start, LocalDateTime.now());
        return cryptoResponseMapper.toCryptoCandleResponses(candles);
    }
}
