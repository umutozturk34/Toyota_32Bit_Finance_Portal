package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.app.dto.response.overview.MoverData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.market.core.cache.TopMoversRedisService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketAssetProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoverWidgetProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TopMoversRedisService topMoversCache;
    private MarketAssetProvider stockProvider;
    private MoverWidgetProvider provider;

    @BeforeEach
    void setUp() {
        topMoversCache = mock(TopMoversRedisService.class);
        stockProvider = mock(MarketAssetProvider.class);
        when(stockProvider.getType()).thenReturn(MarketType.STOCK);
        provider = new MoverWidgetProvider(List.of(stockProvider), topMoversCache, new OverviewDefaults(OverviewPropertiesFixture.standard()));
    }

    private MarketAssetResponse stub(String code) {
        return new MarketAssetResponse(code, code, null, MarketType.STOCK, BigDecimal.ONE, null, BigDecimal.ZERO, null, null);
    }

    private WidgetSection sectionFor(String configJson) throws Exception {
        JsonNode node = objectMapper.readTree(configJson);
        return new WidgetSection("movers-stock", WidgetKind.MOVERS, 0, node);
    }

    @Test
    void should_reportMoversKind_when_kindQueried() {
        assertThat(provider.kind()).isEqualTo(WidgetKind.MOVERS);
    }

    @Test
    void should_returnRedisCachedRows_when_cacheIsWarm() throws Exception {
        when(topMoversCache.getGainers(MarketType.STOCK))
                .thenReturn(List.of(stub("AKBNK.IS"), stub("THYAO.IS")));
        when(topMoversCache.getLosers(MarketType.STOCK))
                .thenReturn(List.of(stub("KRDMD.IS")));

        MoverData data = provider.fetch("user-1", sectionFor("{\"market\":\"STOCK\",\"limit\":5}"));

        assertThat(data.market()).isEqualTo(MarketType.STOCK);
        assertThat(data.gainers()).hasSize(2);
        assertThat(data.losers()).hasSize(1);
        verify(stockProvider, never()).getTopMovers(anyInt(), anyBoolean());
    }

    @Test
    void should_fallBackToProvider_when_redisEmpty() throws Exception {
        when(topMoversCache.getGainers(MarketType.STOCK)).thenReturn(List.of());
        when(topMoversCache.getLosers(MarketType.STOCK)).thenReturn(List.of());
        when(stockProvider.getTopMovers(10, true)).thenReturn(List.of(stub("AKBNK.IS")));
        when(stockProvider.getTopMovers(10, false)).thenReturn(List.of(stub("KRDMD.IS")));

        MoverData data = provider.fetch("user-1", sectionFor("{\"market\":\"STOCK\"}"));

        assertThat(data.gainers()).extracting(MarketAssetResponse::code).containsExactly("AKBNK.IS");
        assertThat(data.losers()).extracting(MarketAssetResponse::code).containsExactly("KRDMD.IS");
    }

    @Test
    void should_capAtConfiguredLimit_when_redisReturnsExtras() throws Exception {
        List<MarketAssetResponse> many = List.of(stub("A"), stub("B"), stub("C"), stub("D"), stub("E"), stub("F"));
        when(topMoversCache.getGainers(MarketType.STOCK)).thenReturn(many);
        when(topMoversCache.getLosers(MarketType.STOCK)).thenReturn(List.of());

        MoverData data = provider.fetch("user-1", sectionFor("{\"market\":\"STOCK\",\"limit\":3}"));

        assertThat(data.gainers()).hasSize(3);
    }

    @Test
    void should_returnEmptyMover_when_marketMissingFromConfig() throws Exception {
        MoverData data = provider.fetch("user-1", sectionFor("{}"));

        assertThat(data.market()).isNull();
        assertThat(data.gainers()).isEmpty();
        assertThat(data.losers()).isEmpty();
    }

    @Test
    void should_returnEmptyMover_when_invalidMarketStringSupplied() throws Exception {
        MoverData data = provider.fetch("user-1", sectionFor("{\"market\":\"INVALID\"}"));

        assertThat(data.market()).isNull();
        assertThat(data.gainers()).isEmpty();
    }

    @Test
    void should_useDefaultLimit_when_configLimitMissingOrZero() throws Exception {
        when(topMoversCache.getGainers(MarketType.STOCK))
                .thenReturn(List.of(stub("A"), stub("B"), stub("C"), stub("D"), stub("E"),
                        stub("F"), stub("G"), stub("H"), stub("I"), stub("J"), stub("K"), stub("L")));
        when(topMoversCache.getLosers(MarketType.STOCK)).thenReturn(List.of());

        MoverData data = provider.fetch("user-1", sectionFor("{\"market\":\"STOCK\",\"limit\":0}"));

        assertThat(data.gainers()).hasSize(10);
    }
}
