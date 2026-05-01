package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.response.BondRateResponse;
import com.finance.backend.dto.response.BondResponse;
import com.finance.backend.dto.response.GroupCount;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.util.EnumParser;
import com.finance.backend.util.LikeSearchSpec;
import com.finance.backend.mapper.BondResponseMapper;
import com.finance.backend.model.Bond;
import com.finance.backend.model.BondRateHistory;
import com.finance.backend.model.BondType;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.repository.BondRateHistoryRepository;
import com.finance.backend.repository.BondRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public PagedResponse<BondResponse> search(String search, String bondType, String sort, String direction, int page, Integer size) {
        Specification<Bond> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    LikeSearchSpec.byFieldsContains(root, cb, search, "seriesCode", "isinCode"));
        }

        BondType filterType = bondType == null || bondType.isBlank()
                ? null
                : EnumParser.parseOrBadRequest(BondType.class, bondType.toUpperCase(), "bond type");
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

    public BondResponse getByCode(String seriesCode) {
        Bond bond = bondCacheService.getSnapshot(seriesCode);
        if (bond == null) {
            throw new ResourceNotFoundException("Bond not found: " + seriesCode);
        }
        return bondResponseMapper.toBondResponse(bond);
    }

    public List<BondRateResponse> getRateHistory(String isinCode, CandlePeriod period) {
        List<BondRateHistory> history = bondRateHistoryRepository
                .findByIsinCodeAndRateDateAfterOrderByRateDateAsc(isinCode, period.toStartDate());
        return bondResponseMapper.toRateResponses(history);
    }

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
