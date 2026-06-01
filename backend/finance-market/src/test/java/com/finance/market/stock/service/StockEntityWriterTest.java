package com.finance.market.stock.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.common.exception.BusinessException;
import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.market.stock.mapper.StockMapper;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.market.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        Instrument asset = Instrument.create(MarketType.STOCK, "THYAO.IS");
        when(stockRepository.findById("THYAO.IS")).thenReturn(Optional.empty());
        when(stockMapper.toEntity(eq(dto), any())).thenReturn(entity);
        when(assetRegistry.upsert(MarketType.STOCK, "THYAO.IS")).thenReturn(asset);

        Stock result = writer.saveSnapshot(dto, "THYAO.IS");

        assertThat(result.getAsset()).isSameAs(asset);
        verify(assetRegistry, times(1)).upsert(MarketType.STOCK, "THYAO.IS");
        verify(stockRepository).save(entity);
    }

    @Test
    void should_upsertAssetAndPreserveExistingStock_when_savingSnapshotForExistingSymbol() {
        YahooStockQuoteDto dto = stockQuote("Akbank", new BigDecimal("80.00"));
        Stock existing = Stock.builder().symbol("AKBNK.IS").build();
        existing.setName("Akbank");
        Instrument asset = Instrument.create(MarketType.STOCK, "AKBNK.IS");
        when(stockRepository.findById("AKBNK.IS")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.STOCK, "AKBNK.IS")).thenReturn(asset);

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

    @Test
    void saveSnapshot_raises_whenDtoIsNull() {
        assertThatThrownBy(() -> writer.saveSnapshot(null, "THYAO.IS"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to fetch");
    }

    @Test
    void saveSnapshot_raises_whenCurrentPriceMissing() {
        YahooStockQuoteDto dto = new YahooStockQuoteDto("THYAO.IS", "THY",
                null, null, null, null, null, null, null, 0L, null, null);

        assertThatThrownBy(() -> writer.saveSnapshot(dto, "THYAO.IS"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing price");
    }

    @Test
    void findExisting_returnsRepositoryResult() {
        Stock stock = Stock.builder().symbol("THYAO.IS").build();
        when(stockRepository.findById("THYAO.IS")).thenReturn(Optional.of(stock));

        assertThat(writer.findExisting("THYAO.IS")).isSameAs(stock);
    }

    @Test
    void findExisting_returnsNull_whenAbsent() {
        when(stockRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThat(writer.findExisting("MISSING")).isNull();
    }

    @Test
    void refreshChangePercentFromCandles_persistsAndReturnsTrue_whenChangeApplied() {
        Stock stock = Stock.builder().symbol("THYAO.IS").build();
        stock.setCurrentPrice(new BigDecimal("100"));
        StockCandle latestCandle = new StockCandle();
        latestCandle.setCandleDate(LocalDateTime.of(2026, 5, 26, 0, 0));
        StockCandle priorCandle = new StockCandle();
        priorCandle.setClose(new BigDecimal("95"));
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("THYAO.IS"))
                .thenReturn(Optional.of(latestCandle));
        when(stockCandleRepository.findFirstByStockSymbolAndCandleDateBeforeOrderByCandleDateDesc(
                eq("THYAO.IS"), eq(latestCandle.getCandleDate())))
                .thenReturn(Optional.of(priorCandle));

        boolean changed = writer.refreshChangePercentFromCandles(stock);

        assertThat(changed).isTrue();
        verify(stockRepository).save(stock);
    }

    @Test
    void refreshChangePercentFromCandles_returnsFalse_whenNoPriorCandle() {
        Stock stock = Stock.builder().symbol("THYAO.IS").build();
        stock.setCurrentPrice(new BigDecimal("100"));
        StockCandle latestCandle = new StockCandle();
        latestCandle.setCandleDate(LocalDateTime.of(2026, 5, 26, 0, 0));
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("THYAO.IS"))
                .thenReturn(Optional.of(latestCandle));
        when(stockCandleRepository.findFirstByStockSymbolAndCandleDateBeforeOrderByCandleDateDesc(
                eq("THYAO.IS"), eq(latestCandle.getCandleDate())))
                .thenReturn(Optional.empty());

        boolean changed = writer.refreshChangePercentFromCandles(stock);

        assertThat(changed).isFalse();
        verify(stockRepository, never()).save(any());
    }

    @Test
    void refreshChangePercentFromCandles_returnsFalse_whenNoCandlesAtAll() {
        Stock stock = Stock.builder().symbol("THYAO.IS").build();
        stock.setCurrentPrice(new BigDecimal("100"));
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("THYAO.IS"))
                .thenReturn(Optional.empty());

        boolean changed = writer.refreshChangePercentFromCandles(stock);

        assertThat(changed).isFalse();
        verify(stockRepository, never()).save(any());
    }

    @Test
    void upsertCandles_savesNewEntities_whenSomeAreNew() {
        Stock stock = Stock.builder().symbol("THYAO.IS").build();
        YahooCandleDto dto = new YahooCandleDto(LocalDateTime.of(2026, 5, 12, 0, 0),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L);
        StockCandle newCandle = new StockCandle();
        when(stockCandleRepository.findByStockSymbolAndCandleDateIn(eq("THYAO.IS"), anyList()))
                .thenReturn(List.of());
        when(stockMapper.toCandleEntity(dto, stock)).thenReturn(newCandle);

        int changed = writer.upsertCandles("THYAO.IS", stock, List.of(dto));

        assertThat(changed).isPositive();
        verify(stockCandleRepository).saveAll(List.of(newCandle));
    }

    @Test
    void saveSnapshot_setsResolvedStockSegment_fromTrackedAsset() {
        YahooStockQuoteDto dto = stockQuote("Akbank", new BigDecimal("80.00"));
        Stock entity = Stock.builder().symbol("AKBNK.IS").build();
        Instrument asset = Instrument.create(MarketType.STOCK, "AKBNK.IS");
        TrackedAssetResponse tracked = TrackedAssetResponse.builder()
                .assetCode("AKBNK.IS").stockSegment(StockSegment.MAIN_INDEX).build();
        when(stockRepository.findById("AKBNK.IS")).thenReturn(Optional.empty());
        when(stockMapper.toEntity(eq(dto), any())).thenReturn(entity);
        when(assetRegistry.upsert(MarketType.STOCK, "AKBNK.IS")).thenReturn(asset);
        when(trackedAssetQueryService.getTrackedAsset(TrackedAssetType.STOCK, "AKBNK.IS"))
                .thenReturn(Optional.of(tracked));

        Stock result = writer.saveSnapshot(dto, "AKBNK.IS");

        assertThat(result.getStockSegment()).isEqualTo(StockSegment.MAIN_INDEX);
    }

    @Test
    void saveSnapshot_fallsBackToEquitySegment_whenTrackedAssetMissing() {
        YahooStockQuoteDto dto = stockQuote("Akbank", new BigDecimal("80.00"));
        Stock entity = Stock.builder().symbol("AKBNK.IS").build();
        Instrument asset = Instrument.create(MarketType.STOCK, "AKBNK.IS");
        when(stockRepository.findById("AKBNK.IS")).thenReturn(Optional.empty());
        when(stockMapper.toEntity(eq(dto), any())).thenReturn(entity);
        when(assetRegistry.upsert(MarketType.STOCK, "AKBNK.IS")).thenReturn(asset);
        when(trackedAssetQueryService.getTrackedAsset(TrackedAssetType.STOCK, "AKBNK.IS"))
                .thenReturn(Optional.empty());

        Stock result = writer.saveSnapshot(dto, "AKBNK.IS");

        assertThat(result.getStockSegment()).isEqualTo(StockSegment.EQUITY);
    }
}
