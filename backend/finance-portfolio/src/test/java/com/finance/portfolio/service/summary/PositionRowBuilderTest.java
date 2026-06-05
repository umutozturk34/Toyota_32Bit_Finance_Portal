package com.finance.portfolio.service.summary;

import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.mapper.PortfolioResponseMapperImpl;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.service.AssetPricingPort.AssetMeta;
import com.finance.shared.service.AssetPricingPort.PriceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionRowBuilderTest {

    @Mock
    private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    private PositionRowBuilder builder;

    @BeforeEach
    void setUp() {
        PortfolioResponseMapper responseMapper = new PortfolioResponseMapperImpl();
        builder = new PositionRowBuilder(responseMapper, assetSnapshotRepository);
    }

    // ---------- toPositionResponse ----------

    @Test
    void should_valueClosedLotAtExitPrice_when_positionIsClosed() {
        PortfolioPosition pos = openLot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("40"));
        pos.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        PriceBundle live = new PriceBundle(new BigDecimal("999"), new AssetMeta("Live Name", "live.png"));

        PositionResponse row = builder.toPositionResponse(pos, live, new BigDecimal("12.5"));

        assertThat(row.currentPriceTry()).isEqualByComparingTo("60");
        assertThat(row.marketValueTry()).isEqualByComparingTo("600.0000");
        assertThat(row.entryValueTry()).isEqualByComparingTo("400.0000");
        assertThat(row.realPnlPercent()).isEqualByComparingTo("12.5");
        verifyNoInteractions(assetSnapshotRepository);
    }

    @Test
    void should_useLivePrice_when_openLotHasLiveBundlePrice() {
        PortfolioPosition pos = openLot(AssetType.STOCK, "AKBNK.IS",
                new BigDecimal("10"), new BigDecimal("50"));
        PriceBundle live = new PriceBundle(new BigDecimal("70"), new AssetMeta("Akbank", "akb.png"));

        PositionResponse row = builder.toPositionResponse(pos, live, null);

        assertThat(row.currentPriceTry()).isEqualByComparingTo("70");
        assertThat(row.marketValueTry()).isEqualByComparingTo("700.0000");
        assertThat(row.assetName()).isEqualTo("Akbank");
        assertThat(row.assetImage()).isEqualTo("akb.png");
        verifyNoInteractions(assetSnapshotRepository);
    }

    @Test
    void should_fallBackToLastSnapshotPrice_when_openLotLivePriceIsNull() {
        Portfolio portfolio = portfolio(7L);
        PortfolioPosition pos = openLotIn(portfolio, AssetType.STOCK, "GARAN.IS",
                new BigDecimal("10"), new BigDecimal("50"));
        PriceBundle stale = new PriceBundle(null, new AssetMeta("Garanti", "gar.png"));
        PortfolioAssetDailySnapshot snap = snapshotWithUnitPrice(new BigDecimal("65"));
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        ArgumentMatchers.eq(7L), ArgumentMatchers.eq(AssetType.STOCK),
                        ArgumentMatchers.eq("GARAN.IS"), ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(Optional.of(snap));

        PositionResponse row = builder.toPositionResponse(pos, stale, null);

        assertThat(row.currentPriceTry()).isEqualByComparingTo("65");
        assertThat(row.marketValueTry()).isEqualByComparingTo("650.0000");
    }

    @Test
    void should_returnNullMarketValue_when_openLotHasNoLiveAndNoSnapshotPrice() {
        Portfolio portfolio = portfolio(9L);
        PortfolioPosition pos = openLotIn(portfolio, AssetType.STOCK, "MISS.IS",
                new BigDecimal("3"), new BigDecimal("100"));
        PriceBundle empty = new PriceBundle(null, new AssetMeta(null, null));
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.any(AssetType.class),
                        ArgumentMatchers.anyString(), ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        PositionResponse row = builder.toPositionResponse(pos, empty, null);

        assertThat(row.currentPriceTry()).isNull();
        assertThat(row.marketValueTry()).isNull();
        assertThat(row.pnlTry()).isNull();
        assertThat(row.pnlPercent()).isNull();
    }

    @Test
    void should_useEmptyBundle_when_bundleArgumentIsNull() {
        Portfolio portfolio = portfolio(11L);
        PortfolioPosition pos = openLotIn(portfolio, AssetType.STOCK, "NULLB.IS",
                new BigDecimal("2"), new BigDecimal("10"));
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.any(AssetType.class),
                        ArgumentMatchers.anyString(), ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        PositionResponse row = builder.toPositionResponse(pos, null, null);

        assertThat(row.currentPriceTry()).isNull();
        assertThat(row.marketValueTry()).isNull();
        assertThat(row.assetName()).isNull();
        assertThat(row.assetImage()).isNull();
    }

    @Test
    void should_defaultMetaToEmpty_when_bundleMetaIsNull() {
        PortfolioPosition pos = openLot(AssetType.STOCK, "META.IS",
                new BigDecimal("1"), new BigDecimal("10"));
        PriceBundle noMeta = new PriceBundle(new BigDecimal("20"), null);

        PositionResponse row = builder.toPositionResponse(pos, noMeta, null);

        assertThat(row.assetName()).isNull();
        assertThat(row.assetImage()).isNull();
        assertThat(row.currentPriceTry()).isEqualByComparingTo("20");
    }

    // ---------- lastSnapshotPrice ----------

    @Test
    void should_returnNull_when_portfolioIsNull() {
        PortfolioPosition pos = openLot(AssetType.STOCK, "NOPF.IS",
                new BigDecimal("1"), new BigDecimal("10"));

        BigDecimal price = builder.lastSnapshotPrice(pos);

        assertThat(price).isNull();
        verifyNoInteractions(assetSnapshotRepository);
    }

    @Test
    void should_returnNull_when_assetTypeIsNull() {
        PortfolioPosition pos = PortfolioPosition.builder()
                .portfolio(portfolio(1L))
                .assetCode("CODE")
                .quantity(BigDecimal.ONE)
                .entryPrice(BigDecimal.TEN)
                .entryDate(LocalDateTime.now())
                .build();

        BigDecimal price = builder.lastSnapshotPrice(pos);

        assertThat(price).isNull();
        verifyNoInteractions(assetSnapshotRepository);
    }

    @Test
    void should_returnNull_when_assetCodeIsNull() {
        PortfolioPosition pos = PortfolioPosition.builder()
                .portfolio(portfolio(1L))
                .assetType(AssetType.STOCK)
                .quantity(BigDecimal.ONE)
                .entryPrice(BigDecimal.TEN)
                .entryDate(LocalDateTime.now())
                .build();

        BigDecimal price = builder.lastSnapshotPrice(pos);

        assertThat(price).isNull();
        verifyNoInteractions(assetSnapshotRepository);
    }

    @Test
    void should_returnUnitPrice_when_repositoryHasSnapshot() {
        PortfolioPosition pos = openLotIn(portfolio(5L), AssetType.CRYPTO, "bitcoin",
                new BigDecimal("1"), new BigDecimal("100000"));
        PortfolioAssetDailySnapshot snap = snapshotWithUnitPrice(new BigDecimal("123456.7890"));
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        ArgumentMatchers.eq(5L), ArgumentMatchers.eq(AssetType.CRYPTO),
                        ArgumentMatchers.eq("bitcoin"), ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(Optional.of(snap));

        BigDecimal price = builder.lastSnapshotPrice(pos);

        assertThat(price).isEqualByComparingTo("123456.7890");
    }

    @Test
    void should_returnNull_when_repositoryHasNoSnapshot() {
        PortfolioPosition pos = openLotIn(portfolio(5L), AssetType.CRYPTO, "ethereum",
                new BigDecimal("1"), new BigDecimal("50000"));
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.any(AssetType.class),
                        ArgumentMatchers.anyString(), ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        BigDecimal price = builder.lastSnapshotPrice(pos);

        assertThat(price).isNull();
    }

    // ---------- buildPositionComparator ----------

    @Test
    void should_sortByProfitPercentAscendingNullsLast_when_sortKeyIsProfitPercent() {
        PositionResponse high = rowWithPnlPercent(new BigDecimal("30"));
        PositionResponse low = rowWithPnlPercent(new BigDecimal("10"));
        PositionResponse missing = rowWithPnlPercent(null);

        List<PositionResponse> sorted = java.util.stream.Stream.of(missing, high, low)
                .sorted(builder.buildPositionComparator("profitPercent")).toList();

        assertThat(sorted).containsExactly(low, high, missing);
    }

    @Test
    void should_sortByProfitAmountAscendingNullsLast_when_sortKeyIsProfitAmount() {
        PositionResponse high = rowWithPnlTry(new BigDecimal("500"));
        PositionResponse low = rowWithPnlTry(new BigDecimal("100"));
        PositionResponse missing = rowWithPnlTry(null);

        List<PositionResponse> sorted = java.util.stream.Stream.of(missing, high, low)
                .sorted(builder.buildPositionComparator("profitAmount")).toList();

        assertThat(sorted).containsExactly(low, high, missing);
    }

    @Test
    void should_sortByAssetCodeNaturally_when_sortKeyIsAssetCode() {
        PositionResponse a = rowWithAssetCode("AKBNK.IS");
        PositionResponse z = rowWithAssetCode("ZRGYO.IS");

        List<PositionResponse> sorted = java.util.stream.Stream.of(z, a)
                .sorted(builder.buildPositionComparator("assetCode")).toList();

        assertThat(sorted).containsExactly(a, z);
    }

    @Test
    void should_sortByQuantityAscending_when_sortKeyIsQuantity() {
        PositionResponse small = rowWithQuantity(new BigDecimal("5"));
        PositionResponse big = rowWithQuantity(new BigDecimal("50"));

        List<PositionResponse> sorted = java.util.stream.Stream.of(big, small)
                .sorted(builder.buildPositionComparator("quantity")).toList();

        assertThat(sorted).containsExactly(small, big);
    }

    @Test
    void should_sortByEntryDateAscendingNullsLast_when_sortKeyIsEntryDate() {
        PositionResponse older = rowWithEntryDate(LocalDateTime.of(2024, 1, 1, 0, 0));
        PositionResponse newer = rowWithEntryDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        PositionResponse missing = rowWithEntryDate(null);

        List<PositionResponse> sorted = java.util.stream.Stream.of(missing, newer, older)
                .sorted(builder.buildPositionComparator("entryDate")).toList();

        assertThat(sorted).containsExactly(older, newer, missing);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "currentValue",
            "unknownKey",
            "null",
    })
    void should_sortByMarketValueAscendingNullsLast_when_sortKeyDefaultsToCurrentValue(String sortBy) {
        PositionResponse high = rowWithMarketValue(new BigDecimal("900"));
        PositionResponse low = rowWithMarketValue(new BigDecimal("100"));
        PositionResponse missing = rowWithMarketValue(null);

        Comparator<PositionResponse> comparator = builder.buildPositionComparator(sortBy);
        List<PositionResponse> sorted = java.util.stream.Stream.of(missing, high, low)
                .sorted(comparator).toList();

        assertThat(sorted).containsExactly(low, high, missing);
    }

    // ---------- toKeys ----------

    @Test
    void should_mapEachPositionToItsAssetKey_when_buildingKeys() {
        PortfolioPosition stock = openLot(AssetType.STOCK, "THYAO.IS", BigDecimal.ONE, BigDecimal.TEN);
        PortfolioPosition crypto = openLot(AssetType.CRYPTO, "bitcoin", BigDecimal.ONE, BigDecimal.TEN);

        List<AssetKey> keys = builder.toKeys(List.of(stock, crypto));

        assertThat(keys).containsExactly(
                new AssetKey(AssetType.STOCK.marketType(), "THYAO.IS"),
                new AssetKey(AssetType.CRYPTO.marketType(), "bitcoin"));
    }

    @Test
    void should_returnEmptyKeys_when_noPositionsGiven() {
        List<AssetKey> keys = builder.toKeys(List.of());

        assertThat(keys).isEmpty();
    }

    // ---------- filterByType ----------

    @Test
    void should_filterToSingleType_when_parseableTypeNameGiven() {
        PortfolioPosition stock = openLot(AssetType.STOCK, "THYAO.IS", BigDecimal.ONE, BigDecimal.TEN);
        PortfolioPosition crypto = openLot(AssetType.CRYPTO, "bitcoin", BigDecimal.ONE, BigDecimal.TEN);

        List<PortfolioPosition> filtered = builder.filterByType(List.of(stock, crypto), "STOCK");

        assertThat(filtered).containsExactly(stock);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null",
            "''",
    })
    void should_returnAllPositions_when_typeNameIsBlankOrNull(String typeName) {
        PortfolioPosition stock = openLot(AssetType.STOCK, "THYAO.IS", BigDecimal.ONE, BigDecimal.TEN);
        PortfolioPosition crypto = openLot(AssetType.CRYPTO, "bitcoin", BigDecimal.ONE, BigDecimal.TEN);
        List<PortfolioPosition> all = List.of(stock, crypto);

        List<PortfolioPosition> filtered = builder.filterByType(all, typeName);

        assertThat(filtered).isSameAs(all);
    }

    // ---------- helpers ----------

    private Portfolio portfolio(Long id) {
        return Portfolio.builder().id(id).userSub("sub").name("pf").build();
    }

    private PortfolioPosition openLot(AssetType type, String code, BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .assetType(type)
                .assetCode(code)
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }

    private PortfolioPosition openLotIn(Portfolio portfolio, AssetType type, String code,
                                        BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .portfolio(portfolio)
                .assetType(type)
                .assetCode(code)
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }

    private PortfolioAssetDailySnapshot snapshotWithUnitPrice(BigDecimal unitPrice) {
        return PortfolioAssetDailySnapshot.builder()
                .unitPriceTry(unitPrice)
                .build();
    }

    private PositionResponse rowWithPnlPercent(BigDecimal pnlPercent) {
        return baseRow().withPnlPercent(pnlPercent).build();
    }

    private PositionResponse rowWithPnlTry(BigDecimal pnlTry) {
        return baseRow().withPnlTry(pnlTry).build();
    }

    private PositionResponse rowWithAssetCode(String assetCode) {
        return baseRow().withAssetCode(assetCode).build();
    }

    private PositionResponse rowWithQuantity(BigDecimal quantity) {
        return baseRow().withQuantity(quantity).build();
    }

    private PositionResponse rowWithEntryDate(LocalDateTime entryDate) {
        return baseRow().withEntryDate(entryDate).build();
    }

    private PositionResponse rowWithMarketValue(BigDecimal marketValue) {
        return baseRow().withMarketValueTry(marketValue).build();
    }

    private RowFixture baseRow() {
        return new RowFixture();
    }

    /**
     * Mutable builder for a {@link PositionResponse} fixture: keeps each comparator test setting only the
     * one field under test while leaving the rest at neutral defaults.
     */
    private static final class RowFixture {
        private String assetCode = "AAA.IS";
        private BigDecimal quantity = BigDecimal.ONE;
        private LocalDateTime entryDate = LocalDateTime.of(2025, 1, 1, 0, 0);
        private BigDecimal marketValueTry = BigDecimal.ZERO;
        private BigDecimal pnlTry = BigDecimal.ZERO;
        private BigDecimal pnlPercent = BigDecimal.ZERO;

        RowFixture withAssetCode(String v) { this.assetCode = v; return this; }
        RowFixture withQuantity(BigDecimal v) { this.quantity = v; return this; }
        RowFixture withEntryDate(LocalDateTime v) { this.entryDate = v; return this; }
        RowFixture withMarketValueTry(BigDecimal v) { this.marketValueTry = v; return this; }
        RowFixture withPnlTry(BigDecimal v) { this.pnlTry = v; return this; }
        RowFixture withPnlPercent(BigDecimal v) { this.pnlPercent = v; return this; }

        PositionResponse build() {
            return new PositionResponse(1L, "STOCK", assetCode, "name", "img.png",
                    quantity, entryDate, BigDecimal.TEN, null, null, null,
                    BigDecimal.TEN, BigDecimal.TEN, marketValueTry, pnlTry, pnlPercent, null, null);
        }
    }
}
