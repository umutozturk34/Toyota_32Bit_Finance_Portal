package com.finance.market.commodity.service;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.market.commodity.mapper.CommodityMapper;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.market.commodity.model.CommoditySnapshotInput;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import com.finance.market.commodity.repository.CommodityRepository;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.service.AssetRegistryService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommodityEntityWriterTest {

    private static final String CODE = "GC=F";

    @Mock private CommodityRepository commodityRepository;
    @Mock private CommodityCandleRepository candleRepository;
    @Mock private CommodityMapper commodityMapper;
    @Mock private AssetRegistryService assetRegistry;

    private CommodityEntityWriter writer;

    @BeforeEach
    void setUp() {
        writer = new CommodityEntityWriter(commodityRepository, candleRepository, commodityMapper, assetRegistry);
    }

    @Test
    void applySnapshot_setsYahooSymbol_whenNotAlreadySet() {
        Commodity commodity = commodityWithCode();
        commodity.setCurrentPrice(BigDecimal.ZERO);
        commodity.setCurrentPriceUsd(BigDecimal.ZERO);
        CommoditySnapshotInput snapshot = snapshot();
        Instrument asset = mock(Instrument.class);
        when(assetRegistry.upsert(MarketType.COMMODITY, CODE)).thenReturn(asset);

        writer.applySnapshot(commodity, snapshot, "GCQ", 4);

        assertThat(commodity.getYahooSymbol()).isEqualTo("GCQ");
        assertThat(commodity.getAsset()).isSameAs(asset);
        verify(commodityRepository).save(commodity);
    }

    @Test
    void applySnapshot_preservesExistingYahooSymbol() {
        Commodity commodity = commodityWithCode();
        commodity.setYahooSymbol("PREEXISTING");
        commodity.setCurrentPrice(BigDecimal.ZERO);
        commodity.setCurrentPriceUsd(BigDecimal.ZERO);
        when(assetRegistry.upsert(MarketType.COMMODITY, CODE)).thenReturn(mock(Instrument.class));

        writer.applySnapshot(commodity, snapshot(), "OVERRIDE", 4);

        assertThat(commodity.getYahooSymbol()).isEqualTo("PREEXISTING");
    }

    @Test
    void refreshChangePercentFromCandles_savesAndReturnsTrue_whenUpdateApplied() {
        Commodity commodity = commodityWithCode();
        commodity.setCurrentPrice(new BigDecimal("2500"));
        CommodityCandle c1 = candle(new BigDecimal("2500"), LocalDateTime.now());
        CommodityCandle c2 = candle(new BigDecimal("2400"), LocalDateTime.now().minusDays(1));
        when(candleRepository.findTop2ByCommodityCodeOrderByCandleDateDesc(CODE))
                .thenReturn(List.of(c1, c2));

        boolean changed = writer.refreshChangePercentFromCandles(commodity, 4);

        assertThat(changed).isTrue();
        verify(commodityRepository).save(commodity);
    }

    @Test
    void refreshChangePercentFromCandles_returnsFalseAndSkipsSave_whenNotEnoughCandles() {
        Commodity commodity = commodityWithCode();
        commodity.setCurrentPrice(new BigDecimal("2500"));
        when(candleRepository.findTop2ByCommodityCodeOrderByCandleDateDesc(CODE)).thenReturn(List.of());

        boolean changed = writer.refreshChangePercentFromCandles(commodity, 4);

        assertThat(changed).isFalse();
        verify(commodityRepository, never()).save(any());
    }

    @Test
    void upsertCandles_skipsEntirely_whenCommodityCodeIsBlank() {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode("");

        writer.upsertCandles(commodity, List.of(yahooCandle(LocalDateTime.now())), 4);

        verify(commodityRepository, never()).save(any());
        verify(candleRepository, never()).saveAll(anyList());
    }

    @Test
    void upsertCandles_skipsEntirely_whenCommodityCodeIsNull() {
        Commodity commodity = new Commodity();

        writer.upsertCandles(commodity, List.of(yahooCandle(LocalDateTime.now())), 4);

        verify(commodityRepository, never()).save(any());
    }

    @Test
    void upsertCandles_persistsCommodity_whenNotYetInRepository() {
        Commodity commodity = commodityWithCode();
        when(commodityRepository.findById(CODE)).thenReturn(Optional.empty());
        when(assetRegistry.upsert(MarketType.COMMODITY, CODE)).thenReturn(mock(Instrument.class));
        when(candleRepository.findByCommodityCodeAndCandleDateIn(any(), anyList())).thenReturn(List.of());
        CommodityCandle newCandle = mock(CommodityCandle.class);
        when(commodityMapper.toCandleEntity(any(), any(), any())).thenReturn(newCandle);

        writer.upsertCandles(commodity, List.of(yahooCandle(LocalDateTime.now())), 4);

        verify(commodityRepository).save(commodity);
        verify(candleRepository).saveAll(anyList());
    }

    @Test
    void upsertCandles_doesNotPersistCommodity_whenAlreadyInRepository() {
        Commodity commodity = commodityWithCode();
        when(commodityRepository.findById(CODE)).thenReturn(Optional.of(commodity));
        when(candleRepository.findByCommodityCodeAndCandleDateIn(any(), anyList())).thenReturn(List.of());
        CommodityCandle newCandle = mock(CommodityCandle.class);
        when(commodityMapper.toCandleEntity(any(), any(), any())).thenReturn(newCandle);

        writer.upsertCandles(commodity, List.of(yahooCandle(LocalDateTime.now())), 4);

        verify(commodityRepository, never()).save(commodity);
    }

    private Commodity commodityWithCode() {
        Commodity c = new Commodity();
        c.setCommodityCode(CODE);
        return c;
    }

    private CommoditySnapshotInput snapshot() {
        return new CommoditySnapshotInput(
                new BigDecimal("2500"), new BigDecimal("2480"),
                new BigDecimal("75"), new BigDecimal("74"),
                new BigDecimal("2490"), new BigDecimal("2510"),
                new BigDecimal("2470"), 1000L, null);
    }

    private CommodityCandle candle(BigDecimal close, LocalDateTime ts) {
        CommodityCandle c = new CommodityCandle();
        c.setClose(close);
        c.setCandleDate(ts);
        return c;
    }

    private YahooCandleDto yahooCandle(LocalDateTime ts) {
        return new YahooCandleDto(ts,
                new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("99"), new BigDecimal("100"), 1000L);
    }
}
