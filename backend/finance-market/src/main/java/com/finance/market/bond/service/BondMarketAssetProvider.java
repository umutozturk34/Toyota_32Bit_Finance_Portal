package com.finance.market.bond.service;

import com.finance.common.model.MarketType;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.shared.util.LikeSearchSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.finance.market.core.service.MarketProviderHelper.buildSort;

/**
 * Read-side {@link MarketAssetProvider} for bonds. Since bonds have no TRY price, the response
 * "price" is the simple yield (falling back to coupon rate), and movers/sort use yield. Lookups
 * accept either series code or ISIN; faceting is by bond type.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class BondMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "simpleYield",
            "changePercent", "simpleYield",
            "name", "seriesCode",
            "default", "seriesCode"
    );

    private final BondRepository bondRepository;

    @Override
    public MarketType getType() {
        return MarketType.BOND;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        return bondRepository.findById(code)
                .map(this::toResponse)
                .orElseGet(() -> bondRepository.findAll((root, query, cb) ->
                        cb.equal(root.get("isinCode"), code), PageRequest.of(0, 1))
                        .stream().findFirst().map(this::toResponse).orElse(null));
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters,
                                            String sortBy, String direction, int page, int size) {
        Specification<Bond> spec = buildSpec(searchTerm, filters);
        List<Bond> bonds = bondRepository.findAll(spec,
                PageRequest.of(page, size, buildSort(sortBy, direction, SORT_FIELDS))).getContent();
        return bonds.stream().map(this::toResponse).toList();
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Specification<Bond> spec = (root, query, cb) -> cb.isNotNull(root.get("simpleYield"));
        Sort sort = gainers
                ? Sort.by(Sort.Direction.DESC, "simpleYield")
                : Sort.by(Sort.Direction.ASC, "simpleYield");
        return bondRepository.findAll(spec, PageRequest.of(0, limit, sort)).getContent()
                .stream().map(this::toResponse).toList();
    }

    @Override
    public long count(MarketAssetFilters filters) {
        Specification<Bond> spec = applyFilters((root, query, cb) -> cb.conjunction(), filters);
        return bondRepository.count(spec);
    }

    @Override
    public long countBySearch(String searchTerm, MarketAssetFilters filters) {
        return bondRepository.count(buildSpec(searchTerm, filters));
    }

    private Specification<Bond> buildSpec(String searchTerm, MarketAssetFilters filters) {
        Specification<Bond> spec = (root, query, cb) -> cb.conjunction();
        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    LikeSearchSpec.byFieldsContains(root, cb, searchTerm, "seriesCode", "isinCode"));
        }
        return applyFilters(spec, filters);
    }

    private Specification<Bond> applyFilters(Specification<Bond> spec, MarketAssetFilters filters) {
        if (filters == null) return spec;
        String subType = filters.subType();
        if (subType != null && !subType.isBlank()) {
            try {
                BondType bondType = BondType.valueOf(subType.toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("bondType"), bondType));
            } catch (IllegalArgumentException e) {
                log.debug("Invalid bond subType={} ignored", subType);
            }
        }
        return spec;
    }

    private MarketAssetResponse toResponse(Bond bond) {
        // The displayed price is the bond's clean TRY price (baseIndex); fall back to simple yield then coupon
        // rate only for bonds whose EVDS feed carried no clean price, so the field is never null when any figure
        // exists. Bonds always stay TRY (no FX) — no currency is attached.
        BigDecimal price = bond.getBaseIndex() != null ? bond.getBaseIndex()
                : (bond.getSimpleYield() != null ? bond.getSimpleYield() : bond.getCouponRate());
        String name = bond.getBondType() != null
                ? bond.getBondType().name() + " · " + bond.getSeriesCode()
                : bond.getSeriesCode();
        return new MarketAssetResponse(
                bond.getSeriesCode(),
                name,
                null,
                MarketType.BOND,
                price,
                null,
                null,
                null,
                null
        );
    }
}
