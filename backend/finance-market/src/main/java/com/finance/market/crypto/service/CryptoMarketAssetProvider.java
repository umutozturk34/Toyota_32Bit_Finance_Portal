package com.finance.market.crypto.service;
import com.finance.market.core.service.BaseTrackedMarketAssetProvider;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.crypto.mapper.CryptoResponseMapper;
import com.finance.market.crypto.model.Crypto;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.crypto.repository.CryptoRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Read-side provider for crypto; single-code lookups read the live cache, search/movers use the base. */
@Log4j2
@Service
public class CryptoMarketAssetProvider extends BaseTrackedMarketAssetProvider<Crypto> {

    private static final List<String> SEARCH_FIELDS = List.of("id", "name", "symbol");

    private final MarketCacheService<Crypto> cryptoCacheService;
    private final CryptoResponseMapper cryptoResponseMapper;

    public CryptoMarketAssetProvider(CryptoRepository cryptoRepository,
                                     MarketCacheService<Crypto> cryptoCacheService,
                                     CryptoResponseMapper cryptoResponseMapper,
                                     TrackedAssetQueryService trackedAssetQueryService) {
        super(cryptoRepository, trackedAssetQueryService);
        this.cryptoCacheService = cryptoCacheService;
        this.cryptoResponseMapper = cryptoResponseMapper;
    }

    @Override
    public MarketType getType() {
        return MarketType.CRYPTO;
    }

    @Override
    protected TrackedAssetType trackedAssetType() {
        return TrackedAssetType.CRYPTO;
    }

    @Override
    protected String codeField() {
        return "id";
    }

    @Override
    protected List<String> searchFields() {
        return SEARCH_FIELDS;
    }

    @Override
    protected String changePercentField() {
        return "changePercent";
    }

    @Override
    protected String priceField() {
        return "currentPriceTry";
    }

    @Override
    protected Map<String, String> sortFields() {
        return Map.of(
                "price", "currentPriceTry",
                "changePercent", "changePercent",
                "name", "name",
                "volume", "totalVolume",
                "marketCap", "marketCap",
                "default", "marketCap"
        );
    }

    @Override
    protected Crypto getSnapshotByCode(String code) {
        return cryptoCacheService.getSnapshot(code);
    }

    @Override
    protected List<MarketAssetResponse> mapToResponses(List<Crypto> entities) {
        return cryptoResponseMapper.toMarketAssetResponses(entities);
    }
}
