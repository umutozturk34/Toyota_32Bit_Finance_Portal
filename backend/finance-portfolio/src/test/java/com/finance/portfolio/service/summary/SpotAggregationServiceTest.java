package com.finance.portfolio.service.summary;

import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.shared.service.AssetPricingPort;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.service.AssetPricingPort.AssetMeta;
import com.finance.shared.service.AssetPricingPort.PriceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotAggregationServiceTest {

    @Mock
    private AssetPricingPort pricingPort;

    @Mock
    private PositionRowBuilder positionRowBuilder;

    private SpotAggregationService service;

    private SpotAggregationService service() {
        if (service == null) {
            service = new SpotAggregationService(pricingPort, positionRowBuilder);
        }
        return service;
    }

    private PortfolioPosition openPosition(AssetType type, String code, BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .assetType(type)
                .assetCode(code)
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }

    private PortfolioPosition closedPosition(AssetType type, String code, BigDecimal qty,
                                             BigDecimal entryPrice, BigDecimal exitPrice) {
        PortfolioPosition pos = openPosition(type, code, qty, entryPrice);
        pos.closeWith(LocalDateTime.now(), exitPrice);
        return pos;
    }

    private PortfolioPosition withId(PortfolioPosition pos, Long id) {
        return PortfolioPosition.builder()
                .id(id)
                .assetType(pos.getAssetType())
                .assetCode(pos.getAssetCode())
                .quantity(pos.getQuantity())
                .entryPrice(pos.getEntryPrice())
                .entryDate(pos.getEntryDate())
                .exitDate(pos.getExitDate())
                .exitPrice(pos.getExitPrice())
                .build();
    }

    @Test
    void should_markOpenAtLivePriceAndAddClosedExitCash_when_mixOfOpenAndClosed() {
        PortfolioPosition open = openPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100"), new BigDecimal("40"));
        PortfolioPosition closed = closedPosition(AssetType.CRYPTO, "bitcoin",
                new BigDecimal("1"), new BigDecimal("2000000"), new BigDecimal("2500000"));
        List<PortfolioPosition> positions = List.of(open, closed);
        Map<AssetKey, BigDecimal> prices = new HashMap<>();
        prices.put(open.toAssetKey(), new BigDecimal("60"));
        when(positionRowBuilder.toKeys(anyList())).thenReturn(List.of(open.toAssetKey()));
        when(pricingPort.getPricesTry(any())).thenReturn(prices);

        SpotTotals totals = service().aggregateSpotTotals(positions);

        // open: live 60 * 100 = 6000 value, 40 * 100 = 4000 cost
        assertThat(totals.spotValue).isEqualByComparingTo("6000.0000");
        assertThat(totals.openCost).isEqualByComparingTo("4000.0000");
        // closed: cost 2000000, exit cash 2500000 * 1
        assertThat(totals.closedCost).isEqualByComparingTo("2000000.0000");
        assertThat(totals.closedExitValue).isEqualByComparingTo("2500000.0000");
        // closed lot must never hit the live-price map (only open keys are queried)
        verify(positionRowBuilder, never()).lastSnapshotPrice(closed);
    }

    @Test
    void should_useLastSnapshotPriceFallback_when_livePriceMissingForOpenLot() {
        PortfolioPosition open = openPosition(AssetType.STOCK, "AKBNK.IS", new BigDecimal("10"), new BigDecimal("50"));
        when(positionRowBuilder.toKeys(anyList())).thenReturn(List.of(open.toAssetKey()));
        when(pricingPort.getPricesTry(any())).thenReturn(Map.of());   // live cache miss
        when(positionRowBuilder.lastSnapshotPrice(open)).thenReturn(new BigDecimal("70"));

        SpotTotals totals = service().aggregateSpotTotals(List.of(open));

        // fallback 70 * 10 = 700 value, cost 50 * 10 = 500
        assertThat(totals.spotValue).isEqualByComparingTo("700.0000");
        assertThat(totals.openCost).isEqualByComparingTo("500.0000");
        verify(positionRowBuilder).lastSnapshotPrice(open);
    }

    @Test
    void should_treatOpenLotAsFlatAtEntry_when_neitherLiveNorSnapshotPriceResolves() {
        PortfolioPosition open = openPosition(AssetType.FUND, "AAK", new BigDecimal("5"), new BigDecimal("100"));
        when(positionRowBuilder.toKeys(anyList())).thenReturn(List.of(open.toAssetKey()));
        when(pricingPort.getPricesTry(any())).thenReturn(Map.of());
        when(positionRowBuilder.lastSnapshotPrice(open)).thenReturn(null);   // no snapshot either

        SpotTotals totals = service().aggregateSpotTotals(List.of(open));

        // addOpen flat-when-no-price: value == cost == entry value (100 * 5 = 500)
        assertThat(totals.spotValue).isEqualByComparingTo("500.0000");
        assertThat(totals.openCost).isEqualByComparingTo("500.0000");
    }

    @Test
    void should_returnAllZeroBuckets_when_noPositions() {
        when(positionRowBuilder.toKeys(anyList())).thenReturn(List.of());
        when(pricingPort.getPricesTry(any())).thenReturn(Map.of());

        SpotTotals totals = service().aggregateSpotTotals(List.of());

        assertThat(totals.spotValue).isEqualByComparingTo("0.0000");
        assertThat(totals.openCost).isEqualByComparingTo("0.0000");
        assertThat(totals.closedCost).isEqualByComparingTo("0.0000");
        assertThat(totals.closedExitValue).isEqualByComparingTo("0.0000");
    }

    @Test
    void should_useExitPrice_when_positionClosed() {
        PortfolioPosition closed = withId(
                closedPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("10"), new BigDecimal("40"), new BigDecimal("60")),
                7L);

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(closed), Map.of());

        // closed: exit price 60 * qty 10 = 600 (currentValue uses exit price, ignores bundles/snapshot)
        assertThat(values).containsOnlyKeys(7L);
        assertThat(values.get(7L)).isEqualByComparingTo("600.0000");
        verify(positionRowBuilder, never()).lastSnapshotPrice(any());
    }

    @Test
    void should_skipPosition_when_idIsNull() {
        PortfolioPosition noId = openPosition(AssetType.STOCK, "GARAN.IS", new BigDecimal("5"), new BigDecimal("80"));

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(noId), Map.of());

        assertThat(values).isEmpty();
    }

    @Test
    void should_useBundlePrice_when_openLotHasLiveBundle() {
        PortfolioPosition open = withId(
                openPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("2"), new BigDecimal("2000000")), 11L);
        Map<AssetKey, PriceBundle> bundles = new HashMap<>();
        bundles.put(open.toAssetKey(), new PriceBundle(new BigDecimal("2500000"), new AssetMeta("Bitcoin", null)));

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(open), bundles);

        // live 2500000 * qty 2 = 5000000
        assertThat(values.get(11L)).isEqualByComparingTo("5000000.0000");
        verify(positionRowBuilder, never()).lastSnapshotPrice(any());
    }

    @Test
    void should_fallBackToSnapshotPrice_when_openLotHasNoBundle() {
        PortfolioPosition open = withId(
                openPosition(AssetType.STOCK, "AKBNK.IS", new BigDecimal("10"), new BigDecimal("50")), 12L);
        when(positionRowBuilder.lastSnapshotPrice(open)).thenReturn(new BigDecimal("70"));

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(open), Map.of());

        // snapshot fallback 70 * 10 = 700
        assertThat(values.get(12L)).isEqualByComparingTo("700.0000");
        verify(positionRowBuilder).lastSnapshotPrice(open);
    }

    @Test
    void should_fallBackToSnapshotPrice_when_bundleExistsButPriceIsNull() {
        PortfolioPosition open = withId(
                openPosition(AssetType.STOCK, "ISCTR.IS", new BigDecimal("4"), new BigDecimal("25")), 13L);
        Map<AssetKey, PriceBundle> bundles = new HashMap<>();
        bundles.put(open.toAssetKey(), new PriceBundle(null, new AssetMeta(null, null)));   // bundle present, price null
        when(positionRowBuilder.lastSnapshotPrice(open)).thenReturn(new BigDecimal("30"));

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(open), bundles);

        // null bundle price -> snapshot fallback 30 * 4 = 120
        assertThat(values.get(13L)).isEqualByComparingTo("120.0000");
        verify(positionRowBuilder).lastSnapshotPrice(open);
    }

    @Test
    void should_omitPosition_when_openLotHasNoBundleAndNoSnapshot() {
        PortfolioPosition open = withId(
                openPosition(AssetType.FUND, "AAK", new BigDecimal("5"), new BigDecimal("100")), 14L);
        when(positionRowBuilder.lastSnapshotPrice(open)).thenReturn(null);   // no live, no snapshot

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(open), Map.of());

        // no effective price -> position skipped entirely
        assertThat(values).doesNotContainKey(14L);
        assertThat(values).isEmpty();
    }

    @Test
    void should_fallBackToLiveBundle_when_closedLotHasNullExitPrice() {
        // A closed lot whose exitPrice is null falls through to the current-price branch (still not closed-priced),
        // since `pos.isClosed() && exitPrice != null` is false; with a live bundle that price is used.
        PortfolioPosition closedNoExit = withId(
                openPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("10"), new BigDecimal("40")), 15L);
        closedNoExit.closeWith(LocalDateTime.now(), null);   // closed but exit price unknown
        Map<AssetKey, PriceBundle> bundles = new HashMap<>();
        bundles.put(closedNoExit.toAssetKey(), new PriceBundle(new BigDecimal("55"), new AssetMeta(null, null)));

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(closedNoExit), bundles);

        // closed + null exit: currentPrice stays the live bundle (snapshot fallback only when !closed) -> 55 * 10
        assertThat(values.get(15L)).isEqualByComparingTo("550.0000");
        verify(positionRowBuilder, never()).lastSnapshotPrice(any());
    }

    @Test
    void should_omitClosedLot_when_exitPriceNullAndNoLiveBundle() {
        // Closed + null exit + no live bundle: snapshot fallback is gated on !closed, so no effective price -> skipped.
        PortfolioPosition closedNoExit = withId(
                openPosition(AssetType.STOCK, "AKBNK.IS", new BigDecimal("3"), new BigDecimal("50")), 16L);
        closedNoExit.closeWith(LocalDateTime.now(), null);
        lenient().when(positionRowBuilder.lastSnapshotPrice(any())).thenReturn(new BigDecimal("99"));

        Map<Long, BigDecimal> values = service().currentValuesByPositionId(List.of(closedNoExit), Map.of());

        assertThat(values).isEmpty();
        verify(positionRowBuilder, never()).lastSnapshotPrice(any());
    }
}
