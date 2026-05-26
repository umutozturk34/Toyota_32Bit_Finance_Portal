package com.finance.market.forex.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.forex.mapper.ForexResponseMapper;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexMarketAssetProviderTest {

    @Mock private ForexRepository forexRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Forex> cacheService;
    @Mock private ForexResponseMapper mapper;
    @Mock private com.finance.market.core.service.TrackedAssetQueryService trackedAssetQueryService;

    private ForexMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ForexMarketAssetProvider(forexRepository, cacheService, mapper, trackedAssetQueryService);
    }

    @Test
    void getType_returnsForex() {
        assertThat(provider.getType()).isEqualTo(MarketType.FOREX);
    }

    @Test
    void getByCode_returnsNull_whenSnapshotMissing() {
        when(cacheService.getSnapshot("USD")).thenReturn(null);

        MarketAssetResponse response = provider.getByCode("USD");

        assertThat(response).isNull();
    }

    @Test
    void getByCode_returnsMappedResponse_whenSnapshotPresent() {
        Forex forex = forex("USD");
        MarketAssetResponse expected = response("USD");
        when(cacheService.getSnapshot("USD")).thenReturn(forex);
        when(mapper.toMarketAssetResponses(List.of(forex))).thenReturn(List.of(expected));

        MarketAssetResponse response = provider.getByCode("USD");

        assertThat(response).isSameAs(expected);
    }

    @Test
    void getByCode_returnsNull_whenMapperReturnsEmptyList() {
        Forex forex = forex("USD");
        when(cacheService.getSnapshot("USD")).thenReturn(forex);
        when(mapper.toMarketAssetResponses(List.of(forex))).thenReturn(List.of());

        MarketAssetResponse response = provider.getByCode("USD");

        assertThat(response).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_returnsMappedResponses_withPaginationAndSort() {
        Forex usd = forex("USD");
        Page<Forex> page = new PageImpl<>(List.of(usd));
        when(forexRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(mapper.toMarketAssetResponses(List.of(usd))).thenReturn(List.of(response("USD")));

        List<MarketAssetResponse> result = provider.search("US", null, "price", "asc", 0, 10);

        assertThat(result).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getTopMovers_gainers_usesDescendingChangePercentSort() {
        Page<Forex> page = new PageImpl<>(List.of(forex("USD")));
        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        when(forexRepository.findAll(any(Specification.class), pageCaptor.capture())).thenReturn(page);
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of(response("USD")));

        provider.getTopMovers(5, true);

        Sort sort = pageCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("changePercent").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getTopMovers_losers_usesAscendingChangePercentSort() {
        Page<Forex> page = new PageImpl<>(List.of(forex("USD")));
        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        when(forexRepository.findAll(any(Specification.class), pageCaptor.capture())).thenReturn(page);
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of(response("USD")));

        provider.getTopMovers(5, false);

        Sort sort = pageCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("changePercent").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @SuppressWarnings("unchecked")
    @Test
    void count_delegatesToRepository() {
        when(forexRepository.count(any(Specification.class))).thenReturn(22L);

        long count = provider.count(null);

        assertThat(count).isEqualTo(22L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void countBySearch_appliesSpecification() {
        when(forexRepository.count(any(Specification.class))).thenReturn(3L);

        long count = provider.countBySearch("US", null);

        assertThat(count).isEqualTo(3L);
        verify(forexRepository).count(any(Specification.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void countBySearch_appliesSpecification_withBlankSearchTerm() {
        when(forexRepository.count(any(Specification.class))).thenReturn(22L);

        long count = provider.countBySearch("", null);

        assertThat(count).isEqualTo(22L);
    }

    private Forex forex(String code) {
        Forex f = Forex.builder().build();
        f.setCurrencyCode(code);
        return f;
    }

    private MarketAssetResponse response(String code) {
        return new MarketAssetResponse(code, code, null, MarketType.FOREX,
                null, null, null, LocalDateTime.now(), null);
    }
}
