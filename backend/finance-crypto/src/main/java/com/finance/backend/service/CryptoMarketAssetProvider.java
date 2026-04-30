package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.CryptoResponseMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CryptoRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

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
    protected Crypto getSnapshotByCode(String code) {
        return cryptoCacheService.getSnapshot(code);
    }

    @Override
    protected List<MarketAssetResponse> mapToResponses(List<Crypto> entities) {
        return cryptoResponseMapper.toMarketAssetResponses(entities);
    }
}
