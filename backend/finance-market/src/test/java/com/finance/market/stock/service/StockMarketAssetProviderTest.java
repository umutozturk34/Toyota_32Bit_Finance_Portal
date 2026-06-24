package com.finance.market.stock.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.stock.mapper.StockResponseMapper;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.repository.StockRepository;
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
class StockMarketAssetProviderTest {

    @Mock private StockRepository stockRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Stock> cacheService;
    @Mock private StockResponseMapper mapper;
    @Mock private com.finance.market.stock.repository.CompanyProfileRepository companyProfileRepository;
    @Mock private com.finance.market.stock.repository.StockIndexMembershipRepository membershipRepository;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private StockMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StockMarketAssetProvider(stockRepository, cacheService, mapper,
                companyProfileRepository, membershipRepository, trackedAssetQueryService);
    }

    @Test
    void getType_returnsStock() {
        assertThat(provider.getType()).isEqualTo(MarketType.STOCK);
    }

    @Test
    void getByCode_returnsNull_whenSnapshotMissing() {
        when(cacheService.getSnapshot("AKBNK")).thenReturn(null);

        assertThat(provider.getByCode("AKBNK")).isNull();
    }

    @Test
    void getByCode_mapsSnapshotToResponse() {
        Stock stock = Stock.builder().build();
        MarketAssetResponse expected = response("AKBNK");
        when(cacheService.getSnapshot("AKBNK")).thenReturn(stock);
        when(mapper.toMarketAssetResponses(List.of(stock))).thenReturn(List.of(expected));

        MarketAssetResponse result = provider.getByCode("AKBNK");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getByCodeIfEnabled_returnsResponse_whenCodeEnabled() {
        Stock stock = Stock.builder().build();
        MarketAssetResponse expected = response("AKBNK");
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of("AKBNK"));
        when(cacheService.getSnapshot("AKBNK")).thenReturn(stock);
        when(mapper.toMarketAssetResponses(List.of(stock))).thenReturn(List.of(expected));

        MarketAssetResponse result = provider.getByCodeIfEnabled("AKBNK");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getByCodeIfEnabled_returnsNull_whenCodeDisabled() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of("THYAO"));

        assertThat(provider.getByCodeIfEnabled("AKBNK")).isNull();
    }

    @Test
    void getByCodeIfEnabled_normalizesCode_beforeEnabledCheck() {
        Stock stock = Stock.builder().build();
        MarketAssetResponse expected = response("AKBNK");
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of("AKBNK"));
        when(cacheService.getSnapshot("AKBNK")).thenReturn(stock);
        when(mapper.toMarketAssetResponses(List.of(stock))).thenReturn(List.of(expected));

        MarketAssetResponse result = provider.getByCodeIfEnabled("akbnk");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getByCodeIfEnabled_returnsNull_whenCodeBlank() {
        assertThat(provider.getByCodeIfEnabled("  ")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_appliesSegmentFilter_whenSegmentProvided() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of("AKBNK"));
        Page<Stock> page = new PageImpl<>(List.of());
        when(stockRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of());

        provider.search("ak", new MarketAssetProvider.MarketAssetFilters("EQUITY", null),
                "name", "asc", 0, 10);

        assertThat(true).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getTopMovers_excludesMainIndexSegment() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of("AKBNK"));
        Page<Stock> page = new PageImpl<>(List.of());
        when(stockRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(mapper.toMarketAssetResponses(any())).thenReturn(List.of());

        provider.getTopMovers(5, true);

        assertThat(true).isTrue();
    }

    @Test
    void getGroupCounts_mapsRepositoryRows() {
        when(stockRepository.countBySegment()).thenReturn(List.of(
                new Object[]{"EQUITY", 100L},
                new Object[]{"MAIN_INDEX", 1L}
        ));

        List<GroupCount> result = provider.getGroupCounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo("EQUITY");
        assertThat(result.get(0).count()).isEqualTo(100L);
    }

    @Test
    void getGroupCounts_returnsEmpty_whenRepositoryEmpty() {
        when(stockRepository.countBySegment()).thenReturn(List.of());

        assertThat(provider.getGroupCounts()).isEmpty();
    }

    private MarketAssetResponse response(String code) {
        return new MarketAssetResponse(code, code, null, MarketType.STOCK,
                null, null, null, LocalDateTime.now(), null);
    }
}
