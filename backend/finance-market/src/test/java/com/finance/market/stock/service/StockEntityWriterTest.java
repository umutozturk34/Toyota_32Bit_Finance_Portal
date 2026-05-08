package com.finance.market.stock.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.Asset;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.market.stock.mapper.StockMapper;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.market.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEntityWriterTest {

    @Mock private StockRepository stockRepository;
    @Mock private StockCandleRepository stockCandleRepository;
    @Mock private StockMapper stockMapper;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private AssetRegistryService assetRegistry;
    @Mock private AppProperties appProperties;

    private StockEntityWriter writer;

    @BeforeEach
    void setUp() {
        when(appProperties.getScale()).thenReturn(4);
        writer = new StockEntityWriter(stockRepository, stockCandleRepository, stockMapper,
                trackedAssetQueryService, assetRegistry, appProperties);
    }

    @Test
    void should_upsertAssetAndLinkToStock_when_savingSnapshotForNewSymbol() {
        YahooStockQuoteDto dto = stockQuote("Türk Hava Yolları", new BigDecimal("250.00"));
        Stock entity = Stock.builder().symbol("THYAO.IS").build();
        entity.setName("Türk Hava Yolları");
        Asset asset = Asset.create(MarketType.STOCK, "THYAO.IS", "Türk Hava Yolları");
        when(stockRepository.findById("THYAO.IS")).thenReturn(Optional.empty());
        when(stockMapper.toEntity(eq(dto), any())).thenReturn(entity);
        when(assetRegistry.upsert(MarketType.STOCK, "THYAO.IS", "Türk Hava Yolları")).thenReturn(asset);

        Stock result = writer.saveSnapshot(dto, "THYAO.IS");

        assertThat(result.getAsset()).isSameAs(asset);
        verify(assetRegistry, times(1)).upsert(MarketType.STOCK, "THYAO.IS", "Türk Hava Yolları");
        verify(stockRepository).save(entity);
    }

    @Test
    void should_upsertAssetAndPreserveExistingStock_when_savingSnapshotForExistingSymbol() {
        YahooStockQuoteDto dto = stockQuote("Akbank", new BigDecimal("80.00"));
        Stock existing = Stock.builder().symbol("AKBNK.IS").build();
        existing.setName("Akbank");
        Asset asset = Asset.create(MarketType.STOCK, "AKBNK.IS", "Akbank");
        when(stockRepository.findById("AKBNK.IS")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.STOCK, "AKBNK.IS", "Akbank")).thenReturn(asset);

        Stock result = writer.saveSnapshot(dto, "AKBNK.IS");

        assertThat(result).isSameAs(existing);
        assertThat(result.getAsset()).isSameAs(asset);
        verify(stockMapper).updateEntityFromDto(eq(existing), eq(dto), any());
        verify(stockMapper, never()).toEntity(any(), any());
    }

    private YahooStockQuoteDto stockQuote(String name, BigDecimal price) {
        return new YahooStockQuoteDto(
                name + "_SYMBOL", name, price, null, null, null, null,
                null, null, 0L, null, null);
    }
}
