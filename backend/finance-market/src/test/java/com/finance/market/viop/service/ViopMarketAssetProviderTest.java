package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.market.viop.mapper.ViopMarketResponseMapper;
import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.shared.dto.response.GroupCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViopMarketAssetProviderTest {

    @Mock private ViopContractRepository repository;
    @Mock private ViopMarketResponseMapper mapper;

    private ViopMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ViopMarketAssetProvider(repository, mapper);
    }

    private ViopContract sampleContract() {
        return ViopContract.builder()
                .symbol("F_USDTRY0626")
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .active(true)
                .build();
    }

    @Test
    void should_returnViopMarketType_when_getTypeCalled() {
        assertThat(provider.getType()).isEqualTo(MarketType.VIOP);
    }

    @Test
    void should_returnNull_when_lookupByCodeMisses() {
        when(repository.findBySymbol("F_MISSING")).thenReturn(Optional.empty());

        MarketAssetResponse result = provider.getByCode("F_MISSING");

        assertThat(result).isNull();
    }

    @Test
    void should_returnMappedResponse_when_lookupByCodeMatches() {
        ViopContract contract = sampleContract();
        when(repository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(mapper.toResponse(contract)).thenReturn(new MarketAssetResponse(
                "F_USDTRY0626", "F_USDTRY0626", null, MarketType.VIOP, null, null, null, null, null));

        MarketAssetResponse result = provider.getByCode("F_USDTRY0626");

        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo("F_USDTRY0626");
    }

    @Test
    void should_delegateSearchToRepository_when_searchCalledWithoutFilters() {
        Page<ViopContract> page = new PageImpl<>(List.of(sampleContract()));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponses(any())).thenReturn(List.of());

        provider.search(null, null, "default", "asc", 0, 20);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_applySearchTermFiltering_when_searchTermProvided() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toResponses(any())).thenReturn(List.of());

        provider.search("AKBNK", null, "price", "desc", 0, 10);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_applySegmentFilter_when_filterHasSegment() {
        MarketAssetProvider.MarketAssetFilters filters =
                MarketAssetProvider.MarketAssetFilters.ofSegment("FUTURE");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toResponses(any())).thenReturn(List.of());

        provider.search(null, filters, "name", "asc", 0, 5);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_applySubTypeCategoryFilter_when_filterHasSubType() {
        MarketAssetProvider.MarketAssetFilters filters =
                new MarketAssetProvider.MarketAssetFilters(null, "CURRENCY_FUTURE_TRY");
        when(repository.count(any(Specification.class))).thenReturn(2L);

        long count = provider.count(filters);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void should_delegateCountBySearchToRepository_when_invokedWithTerm() {
        when(repository.count(any(Specification.class))).thenReturn(5L);

        long count = provider.countBySearch("AKBNK", null);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void should_orderTopMoversDescending_when_gainersTrue() {
        ViopContract winner = ViopContract.builder().symbol("F_X").changePercent(new BigDecimal("3.5"))
                .active(true).build();
        Page<ViopContract> page = new PageImpl<>(List.of(winner));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponses(any())).thenReturn(List.of());

        provider.getTopMovers(5, true);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_orderTopMoversAscending_when_gainersFalse() {
        Page<ViopContract> page = new PageImpl<>(List.of());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponses(any())).thenReturn(List.of());

        provider.getTopMovers(5, false);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_groupContractsByCategoryCount_when_getGroupCountsCalled() {
        ViopContract c1 = sampleContract();
        ViopContract c2 = ViopContract.builder().symbol("F_Y").category(ViopCategory.INDEX_FUTURE).active(true).build();
        ViopContract c3 = ViopContract.builder().symbol("F_Z").category(ViopCategory.CURRENCY_FUTURE_TRY).active(true).build();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2, c3));

        List<GroupCount> result = provider.getGroupCounts();

        assertThat(result).extracting(GroupCount::type)
                .containsExactlyInAnyOrder("CURRENCY_FUTURE_TRY", "INDEX_FUTURE");
        GroupCount currency = result.stream().filter(g -> g.type().equals("CURRENCY_FUTURE_TRY"))
                .findFirst().orElseThrow();
        assertThat(currency.count()).isEqualTo(2L);
    }

    @Test
    void should_ignoreContractsWithNullCategory_when_grouping() {
        ViopContract uncategorised = ViopContract.builder().symbol("F_Q").category(null).active(true).build();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(uncategorised));

        List<GroupCount> result = provider.getGroupCounts();

        assertThat(result).isEmpty();
    }
}
