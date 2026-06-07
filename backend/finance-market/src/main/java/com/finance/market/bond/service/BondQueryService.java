package com.finance.market.bond.service;
import com.finance.market.core.cache.MarketCacheService;



import com.finance.common.config.AppProperties;
import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.dto.response.BondResponse;
import com.finance.shared.dto.response.GroupCount;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.shared.util.EnumParser;
import com.finance.shared.util.LikeSearchSpec;
import com.finance.market.bond.mapper.BondResponseMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import com.finance.shared.model.CandlePeriod;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bond-specific read API: paged search with type filtering, single-bond lookup (cache-backed),
 * coupon-rate history over a period, and per-type counts.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BondQueryService {

    private final AppProperties appProperties;
    private final BondRepository bondRepository;
    private final BondRateHistoryRepository bondRateHistoryRepository;
    private final BondResponseMapper bondResponseMapper;
    private final MarketCacheService<Bond> bondCacheService;

    /**
     * Paged bond search. A non-blank {@code search} matches the series or ISIN code (contains); a non-blank
     * {@code bondType} filters by type. Sorting falls back to simple yield when no sort field is given; the
     * page size is clamped to the configured default/max bounds.
     *
     * @throws com.finance.common.exception.BadRequestException if {@code bondType} is not a valid type
     */
    public PagedResponse<BondResponse> search(String search, String bondType, String sort, String direction, int page, Integer size) {
        Specification<Bond> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    LikeSearchSpec.byFieldsContains(root, cb, search, "seriesCode", "isinCode"));
        }

        BondType filterType = bondType == null || bondType.isBlank()
                ? null
                : EnumParser.parseOrBadRequest(BondType.class, bondType.toUpperCase(), "enum.field.bondType");
        if (filterType != null) {
            BondType fixed = filterType;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("bondType"), fixed));
        }

        int resolvedSize = resolvePageSize(size);
        PageRequest pageRequest = (sort != null && !sort.isBlank())
                ? PageRequest.of(page, resolvedSize, buildSort(sort, direction))
                : PageRequest.of(page, resolvedSize);
        Page<Bond> result = bondRepository.findAll(spec, pageRequest);

        List<BondResponse> bonds = bondResponseMapper.toBondResponses(result.getContent());
        return PagedResponse.of(bonds, page, resolvedSize, result.getTotalElements());
    }

    /**
     * Single bond by series code, served from the snapshot cache.
     *
     * @throws ResourceNotFoundException if no bond is cached for the series code
     */
    public BondResponse getByCode(String seriesCode) {
        Bond bond = bondCacheService.getSnapshot(seriesCode);
        if (bond == null) {
            throw new ResourceNotFoundException("error.market.bondNotFound", seriesCode);
        }
        return bondResponseMapper.toBondResponse(bond);
    }

    /**
     * Coupon-rate history for the bond (by ISIN), ascending by date, from the period's start date to now.
     */
    public List<BondRateResponse> getRateHistory(String isinCode, CandlePeriod period) {
        List<BondRateHistory> history = bondRateHistoryRepository
                .findByIsinCodeAndRateDateAfterOrderByRateDateAsc(isinCode, period.toStartDate());
        return bondResponseMapper.toRateResponses(history);
    }

    /** Bond counts grouped by type (for type-filter facet badges). */
    public List<GroupCount> getTypeCounts() {
        return bondRepository.countByBondType().stream()
                .map(row -> new GroupCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }

    private Sort buildSort(String sortBy, String direction) {
        String field = switch (sortBy) {
            case "couponRate" -> "couponRate";
            case "baseIndex" -> "baseIndex";
            case "maturityEnd" -> "maturityEnd";
            case "seriesCode" -> "seriesCode";
            default -> "simpleYield";
        };
        return Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, field);
    }

    private int resolvePageSize(Integer size) {
        AppProperties.BondPage pagination = appProperties.getPagination().getBond();
        int requestedSize = size == null ? pagination.getDefaultSize() : size;
        return Math.max(1, Math.min(requestedSize, pagination.getMaxSize()));
    }
}
