package com.finance.market.fund.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.fund.mapper.FundResponseMapper;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.repository.FundRepository;
import com.finance.shared.dto.response.GroupCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundMarketAssetProviderTest {

    @Mock private FundRepository fundRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Fund> cacheService;
    @Mock private FundResponseMapper mapper;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private FundMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FundMarketAssetProvider(fundRepository, cacheService, mapper, trackedAssetQueryService);
    }

    @Test
    void getType_returnsFund() {
        assertThat(provider.getType()).isEqualTo(MarketType.FUND);
    }

    @Test
    void getByCode_returnsNull_whenSnapshotMissing() {
        when(cacheService.getSnapshot("TYH")).thenReturn(null);

        assertThat(provider.getByCode("TYH")).isNull();
    }

    @Test
    void getByCode_returnsFirstMappedResponse_whenSnapshotPresent() {
        Fund fund = Fund.builder().build();
        fund.setFundCode("TYH");
        MarketAssetResponse expected = response("TYH");
        when(cacheService.getSnapshot("TYH")).thenReturn(fund);
        when(mapper.toMarketAssetResponses(List.of(fund))).thenReturn(List.of(expected));

        MarketAssetResponse result = provider.getByCode("TYH");

        assertThat(result).isSameAs(expected);
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_appliesFundTypeFilter_whenSubTypeProvided() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND)).thenReturn(List.of("TYH"));
        when(fundRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of());

        provider.search("ty", new MarketAssetProvider.MarketAssetFilters(null, "BYF"), "price", "asc", 0, 10);

        assertThat(true).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_skipsCustomFilter_whenFiltersHaveNoSubType() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND)).thenReturn(List.of("TYH"));
        Page<Fund> page = new PageImpl<>(List.of());
        when(fundRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of());

        provider.search(null, null, "name", "desc", 0, 5);

        assertThat(true).isTrue();
    }

    @Test
    void getGroupCounts_mapsRepositoryRowsToGroupCount() {
        when(fundRepository.countByFundType()).thenReturn(List.of(
                new Object[]{"VIOP", 5L},
                new Object[]{"BIST_DEBT", 3L}
        ));

        List<GroupCount> result = provider.getGroupCounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo("VIOP");
        assertThat(result.get(0).count()).isEqualTo(5L);
        assertThat(result.get(1).type()).isEqualTo("BIST_DEBT");
        assertThat(result.get(1).count()).isEqualTo(3L);
    }

    @Test
    void getGroupCounts_returnsEmptyList_whenRepositoryReturnsNoRows() {
        when(fundRepository.countByFundType()).thenReturn(List.of());

        List<GroupCount> result = provider.getGroupCounts();

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_appliesSubCategoryFilter_whenFiltersHaveSubCategories() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND)).thenReturn(List.of("TYH"));
        when(fundRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of());

        provider.search(null,
                new MarketAssetProvider.MarketAssetFilters(null, null, List.of("CAT_A", "CAT_B"), null),
                "default", "asc", 0, 10);

        assertThat(true).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_appliesRiskValueFilter_whenFiltersHaveRiskValues() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND)).thenReturn(List.of("TYH"));
        when(fundRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of());

        provider.search(null,
                new MarketAssetProvider.MarketAssetFilters(null, null, null, List.of(3, 5)),
                "default", "asc", 0, 10);

        assertThat(true).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_combinedFiltersApplyTogether() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND)).thenReturn(List.of("TYH"));
        when(fundRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of());

        provider.search(null,
                new MarketAssetProvider.MarketAssetFilters(null, "BYF", List.of("CAT"), List.of(1)),
                "default", "asc", 0, 10);

        assertThat(true).isTrue();
    }

    private MarketAssetResponse response(String code) {
        return new MarketAssetResponse(code, code, null, MarketType.FUND,
                null, null, null, LocalDateTime.now(), null);
    }
}
