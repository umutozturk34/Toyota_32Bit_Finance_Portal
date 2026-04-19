package com.finance.backend.service;

import com.finance.backend.dto.response.CryptoMetadata;
import com.finance.backend.dto.response.MarketAssetMetadata;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.StockMetadata;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.StockSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTopMoversFilterTest {

    @Test
    void stockListHasMainIndexEntriesRemoved() {
        MarketAssetResponse equity = stock("THYAO", StockSegment.EQUITY);
        MarketAssetResponse index = stock("XU100", StockSegment.MAIN_INDEX);
        MarketAssetResponse secondaryIndex = stock("XUSIN", StockSegment.SECONDARY_INDEX);

        List<MarketAssetResponse> filtered = MarketTopMoversFilter.apply(
                MarketType.STOCK, List.of(equity, index, secondaryIndex));

        assertThat(filtered).containsExactly(equity, secondaryIndex);
    }

    @Test
    void stockListKeepsItemsWithNullMetadata() {
        MarketAssetResponse noMeta = new MarketAssetResponse(
                "ABCDE", "Some Corp", null, MarketType.STOCK,
                BigDecimal.ONE, null, null, null, null);

        List<MarketAssetResponse> filtered = MarketTopMoversFilter.apply(
                MarketType.STOCK, List.of(noMeta));

        assertThat(filtered).containsExactly(noMeta);
    }

    @Test
    void stockListKeepsItemsWithWrongMetadataType() {
        MarketAssetResponse wrongMeta = new MarketAssetResponse(
                "ABCDE", "Some Corp", null, MarketType.STOCK,
                BigDecimal.ONE, null, null, null,
                new CryptoMetadata(null, null, null, null));

        List<MarketAssetResponse> filtered = MarketTopMoversFilter.apply(
                MarketType.STOCK, List.of(wrongMeta));

        assertThat(filtered).containsExactly(wrongMeta);
    }

    @ParameterizedTest
    @EnumSource(value = MarketType.class, names = {"CRYPTO", "FOREX", "FUND"})
    void nonStockTypesAreReturnedUnchanged(MarketType type) {
        MarketAssetResponse stockLikeItem = stock("XU100", StockSegment.MAIN_INDEX);
        List<MarketAssetResponse> input = List.of(stockLikeItem);

        List<MarketAssetResponse> filtered = MarketTopMoversFilter.apply(type, input);

        assertThat(filtered).isSameAs(input);
    }

    @Test
    void emptyListReturnsEmpty() {
        List<MarketAssetResponse> filtered = MarketTopMoversFilter.apply(MarketType.STOCK, List.of());

        assertThat(filtered).isEmpty();
    }

    private static MarketAssetResponse stock(String code, StockSegment segment) {
        MarketAssetMetadata metadata = new StockMetadata(segment, null, null, null, null, null);
        return new MarketAssetResponse(code, code, null, MarketType.STOCK,
                BigDecimal.ONE, null, null, null, metadata);
    }
}
