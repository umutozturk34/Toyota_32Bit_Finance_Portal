package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.app.dto.response.overview.AssetCardsData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.dto.response.MarketAssetResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.common.service.MarketAssetProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetCardsWidgetProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MarketAssetProvider stockProvider;
    private MarketAssetProvider cryptoProvider;
    private OverviewDefaults defaults;
    private AssetCardsWidgetProvider provider;

    @BeforeEach
    void setUp() {
        stockProvider = mock(MarketAssetProvider.class);
        when(stockProvider.getType()).thenReturn(MarketType.STOCK);
        cryptoProvider = mock(MarketAssetProvider.class);
        when(cryptoProvider.getType()).thenReturn(MarketType.CRYPTO);
        defaults = new OverviewDefaults();
        provider = new AssetCardsWidgetProvider(List.of(stockProvider, cryptoProvider), defaults);
    }

    private MarketAssetResponse stub(MarketType type, String code) {
        return new MarketAssetResponse(code, code, null, type, BigDecimal.ONE, null, BigDecimal.ZERO, null, null);
    }

    @Test
    void should_reportAssetCardsKind_when_kindQueried() {
        assertThat(provider.kind()).isEqualTo(WidgetKind.ASSET_CARDS);
    }

    @Test
    void should_resolveExplicitCodes_when_configCarriesAssetReferences() throws Exception {
        JsonNode config = objectMapper.readTree("""
                {"assetCodes":[{"type":"STOCK","code":"XU100.IS"},{"type":"CRYPTO","code":"BTC"}]}""");
        when(stockProvider.getByCode("XU100.IS")).thenReturn(stub(MarketType.STOCK, "XU100.IS"));
        when(cryptoProvider.getByCode("BTC")).thenReturn(stub(MarketType.CRYPTO, "BTC"));

        AssetCardsData data = provider.fetch("user-1", new WidgetSection("a-1", WidgetKind.ASSET_CARDS, 0, config));

        assertThat(data.items()).hasSize(2);
        assertThat(data.items()).extracting(MarketAssetResponse::code).containsExactly("XU100.IS", "BTC");
    }

    @Test
    void should_returnDefaultReferences_when_assetCodesFieldMissing() throws Exception {
        JsonNode config = objectMapper.readTree("{}");
        for (AssetCardsWidgetProvider.AssetReference ref : defaults.defaultAssetReferences()) {
            MarketAssetProvider p = ref.type() == MarketType.STOCK ? stockProvider
                    : ref.type() == MarketType.CRYPTO ? cryptoProvider : null;
            if (p != null) when(p.getByCode(ref.code())).thenReturn(stub(ref.type(), ref.code()));
        }

        AssetCardsData data = provider.fetch("user-1", new WidgetSection("a-1", WidgetKind.ASSET_CARDS, 0, config));

        assertThat(data.items()).isNotEmpty();
    }

    @Test
    void should_returnEmptyItems_when_configIsExplicitEmptyArray() throws Exception {
        JsonNode config = objectMapper.readTree("{\"assetCodes\":[]}");

        AssetCardsData data = provider.fetch("user-1", new WidgetSection("a-1", WidgetKind.ASSET_CARDS, 0, config));

        assertThat(data.items()).isEmpty();
    }

    @Test
    void should_skipUnknownType_when_referenceTypeHasNoProvider() throws Exception {
        JsonNode config = objectMapper.readTree("""
                {"assetCodes":[{"type":"FOREX","code":"USDTRY"},{"type":"STOCK","code":"XU100.IS"}]}""");
        when(stockProvider.getByCode("XU100.IS")).thenReturn(stub(MarketType.STOCK, "XU100.IS"));

        AssetCardsData data = provider.fetch("user-1", new WidgetSection("a-1", WidgetKind.ASSET_CARDS, 0, config));

        assertThat(data.items()).extracting(MarketAssetResponse::code).containsExactly("XU100.IS");
    }

    @Test
    void should_skipMissingCode_when_providerThrowsResourceNotFound() throws Exception {
        JsonNode config = objectMapper.readTree("""
                {"assetCodes":[{"type":"STOCK","code":"DEAD.IS"},{"type":"STOCK","code":"XU100.IS"}]}""");
        when(stockProvider.getByCode("DEAD.IS"))
                .thenThrow(new ResourceNotFoundException("not found"));
        when(stockProvider.getByCode("XU100.IS")).thenReturn(stub(MarketType.STOCK, "XU100.IS"));

        AssetCardsData data = provider.fetch("user-1", new WidgetSection("a-1", WidgetKind.ASSET_CARDS, 0, config));

        assertThat(data.items()).extracting(MarketAssetResponse::code).containsExactly("XU100.IS");
    }

    @Test
    void should_ignoreInvalidEnumValue_when_typeStringNotMarketType() throws Exception {
        JsonNode config = objectMapper.readTree("""
                {"assetCodes":[{"type":"BAD_TYPE","code":"X"}]}""");

        AssetCardsData data = provider.fetch("user-1", new WidgetSection("a-1", WidgetKind.ASSET_CARDS, 0, config));

        assertThat(data.items()).isEmpty();
        verify(stockProvider, never()).getByCode(anyString());
    }

    @Test
    void should_capAtTwelveItems_when_configExceedsLimit() throws Exception {
        StringBuilder json = new StringBuilder("{\"assetCodes\":[");
        for (int i = 0; i < 20; i++) {
            if (i > 0) json.append(",");
            json.append("{\"type\":\"STOCK\",\"code\":\"S").append(i).append("\"}");
        }
        json.append("]}");
        JsonNode config = objectMapper.readTree(json.toString());
        when(stockProvider.getByCode(anyString())).thenAnswer(inv -> stub(MarketType.STOCK, inv.getArgument(0)));

        AssetCardsData data = provider.fetch("user-1", new WidgetSection("a-1", WidgetKind.ASSET_CARDS, 0, config));

        assertThat(data.items()).hasSize(12);
    }
}
