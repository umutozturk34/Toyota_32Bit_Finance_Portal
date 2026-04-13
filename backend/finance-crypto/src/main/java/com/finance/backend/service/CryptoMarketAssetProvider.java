package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.CryptoResponseMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CryptoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.finance.backend.service.MarketProviderHelper.applyDisplayNames;
import static com.finance.backend.service.MarketProviderHelper.buildSort;

@Log4j2
@Service
@RequiredArgsConstructor
public class CryptoMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "currentPriceTry",
            "changePercent", "changePercent",
            "name", "name",
            "default", "changePercent"
    );

    private final CryptoRepository cryptoRepository;
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final CryptoResponseMapper cryptoResponseMapper;
    private final TrackedAssetService trackedAssetService;

    @Override
    public MarketType getType() {
        return MarketType.CRYPTO;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        Crypto crypto = cryptoCacheService.getSnapshot(code);
        if (crypto == null) return null;
        return withDisplayNames(cryptoResponseMapper.toMarketAssetResponses(List.of(crypto))).stream().findFirst().orElse(null);
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, String sortBy, String direction, int page, int size) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.CRYPTO));
        Specification<Crypto> spec = buildSpecification(searchTerm, enabledCodes);

        List<Crypto> cryptos = cryptoRepository.findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, SORT_FIELDS))).getContent();
        return withDisplayNames(cryptoResponseMapper.toMarketAssetResponses(cryptos));
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.CRYPTO));
        Specification<Crypto> spec = enabledCodesSpec(enabledCodes)
                .and(nonNullChangePercent());

        Sort sort = gainers
                ? Sort.by(Sort.Direction.DESC, "changePercent")
                : Sort.by(Sort.Direction.ASC, "changePercent");

        List<Crypto> cryptos = cryptoRepository.findAll(spec, PageRequest.of(0, limit, sort)).getContent();
        return withDisplayNames(cryptoResponseMapper.toMarketAssetResponses(cryptos));
    }

    @Override
    public long count() {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.CRYPTO));
        return cryptoRepository.count(enabledCodesSpec(enabledCodes));
    }

    @Override
    public long countBySearch(String searchTerm) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.CRYPTO));
        return cryptoRepository.count(buildSpecification(searchTerm, enabledCodes));
    }

    private Specification<Crypto> buildSpecification(String searchTerm, Set<String> enabledCodes) {
        Specification<Crypto> spec = enabledCodesSpec(enabledCodes);
        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("id")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("symbol")), pattern)));
        }
        return spec;
    }

    private Specification<Crypto> enabledCodesSpec(Set<String> enabledCodes) {
        return (root, query, cb) -> root.get("id").in(enabledCodes);
    }

    private Specification<Crypto> nonNullChangePercent() {
        return (root, query, cb) -> cb.isNotNull(root.get("changePercent"));
    }

    private List<MarketAssetResponse> withDisplayNames(List<MarketAssetResponse> responses) {
        return applyDisplayNames(responses, trackedAssetService.getEnabledDisplayNameMap(TrackedAssetType.CRYPTO));
    }
}
