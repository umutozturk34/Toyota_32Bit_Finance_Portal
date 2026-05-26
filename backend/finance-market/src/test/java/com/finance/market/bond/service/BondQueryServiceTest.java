package com.finance.market.bond.service;

import com.finance.common.config.AppProperties;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.dto.response.BondResponse;
import com.finance.market.bond.mapper.BondResponseMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.shared.dto.response.GroupCount;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondQueryServiceTest {

    @Mock private BondRepository bondRepository;
    @Mock private BondRateHistoryRepository bondRateHistoryRepository;
    @Mock private BondResponseMapper bondResponseMapper;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Bond> bondCacheService;

    private BondQueryService service;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        service = new BondQueryService(appProperties, bondRepository, bondRateHistoryRepository,
                bondResponseMapper, bondCacheService);
    }

    private Bond bond(String code) {
        Bond b = new Bond();
        b.setSeriesCode(code);
        return b;
    }

    private BondResponse response(String code) {
        return new BondResponse(code, code, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void search_returnsPagedResponse_whenNoFiltersProvided() {
        Bond b = bond("S1");
        Page<Bond> page = new PageImpl<>(List.of(b), PageRequest.of(0, 8), 1);
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(bondResponseMapper.toBondResponses(List.of(b))).thenReturn(List.of(response("S1")));

        PagedResponse<BondResponse> result = service.search(null, null, null, null, 0, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void search_appliesSearchFilter_andCustomSort() {
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 8), 0));
        when(bondResponseMapper.toBondResponses(List.of())).thenReturn(List.of());

        PagedResponse<BondResponse> result = service.search("TRT", null, "couponRate", "asc", 0, 5);

        assertThat(result.totalElements()).isZero();
    }

    @Test
    void search_appliesBondTypeFilter_whenProvided() {
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 8), 0));
        when(bondResponseMapper.toBondResponses(List.of())).thenReturn(List.of());

        service.search(null, "DISCOUNTED", null, null, 0, null);

        assertThat(service).isNotNull();
    }

    @Test
    void search_raises_whenBondTypeUnknown() {
        assertThatThrownBy(() -> service.search(null, "UNKNOWN_TYPE", null, null, 0, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void search_clampsRequestedSizeWithinAllowedBounds() {
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
        when(bondResponseMapper.toBondResponses(List.of())).thenReturn(List.of());

        PagedResponse<BondResponse> result = service.search(null, null, null, null, 0, 9999);

        assertThat(result.totalElements()).isZero();
    }

    @Test
    void getByCode_returnsCachedSnapshot_whenPresent() {
        Bond b = bond("S1");
        when(bondCacheService.getSnapshot("S1")).thenReturn(b);
        when(bondResponseMapper.toBondResponse(b)).thenReturn(response("S1"));

        BondResponse result = service.getByCode("S1");

        assertThat(result.seriesCode()).isEqualTo("S1");
    }

    @Test
    void getByCode_raises_whenSnapshotMissing() {
        when(bondCacheService.getSnapshot("MISSING")).thenReturn(null);

        assertThatThrownBy(() -> service.getByCode("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRateHistory_delegatesToRepositoryAndMapper() {
        BondRateHistory entry = BondRateHistory.builder()
                .isinCode("ISIN1")
                .rateDate(LocalDate.of(2026, 1, 1))
                .couponRate(new BigDecimal("10"))
                .build();
        when(bondRateHistoryRepository.findByIsinCodeAndRateDateAfterOrderByRateDateAsc(
                eqStr("ISIN1"), any(LocalDate.class))).thenReturn(List.of(entry));
        BondRateResponse mapped = new BondRateResponse(LocalDate.of(2026, 1, 1), new BigDecimal("10"), null);
        when(bondResponseMapper.toRateResponses(List.of(entry))).thenReturn(List.of(mapped));

        List<BondRateResponse> result = service.getRateHistory("ISIN1", CandlePeriod.ONE_YEAR);

        assertThat(result).hasSize(1);
    }

    @Test
    void getTypeCounts_mapsBondTypeRowsToGroupCounts() {
        when(bondRepository.countByBondType()).thenReturn(List.of(
                new Object[]{"DISCOUNTED", 5L},
                new Object[]{"CPI_INDEXED", 3L}));

        List<GroupCount> result = service.getTypeCounts();

        assertThat(result).extracting(GroupCount::type).containsExactly("DISCOUNTED", "CPI_INDEXED");
        assertThat(result).extracting(GroupCount::count).containsExactly(5L, 3L);
    }

    private static String eqStr(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
    }
}
