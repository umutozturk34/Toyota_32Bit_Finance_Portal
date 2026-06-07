package com.finance.market.bond.service;

import com.finance.common.model.MarketType;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider.MarketAssetFilters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BondMarketAssetProviderTest {

    @Mock private BondRepository bondRepository;

    private BondMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new BondMarketAssetProvider(bondRepository);
    }

    private Bond bond(String seriesCode, String isin, BondType type,
                     BigDecimal yield, BigDecimal coupon) {
        Bond b = new Bond();
        b.setSeriesCode(seriesCode);
        b.setIsinCode(isin);
        b.setBondType(type);
        b.setSimpleYield(yield);
        b.setCouponRate(coupon);
        return b;
    }

    @Test
    void should_returnBondType_when_getTypeCalled() {
        // Arrange
        // provider built in setUp

        // Act
        MarketType type = provider.getType();

        // Assert
        assertThat(type).isEqualTo(MarketType.BOND);
    }

    @Test
    void should_returnResponse_when_bondFoundBySeriesCode() {
        // Arrange
        Bond b = bond("TRT250101T15", "ISIN1", BondType.FIXED_COUPON, new BigDecimal("45.50"), new BigDecimal("10"));
        when(bondRepository.findById("TRT250101T15")).thenReturn(Optional.of(b));

        // Act
        MarketAssetResponse response = provider.getByCode("TRT250101T15");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("TRT250101T15");
        assertThat(response.name()).isEqualTo("FIXED_COUPON · TRT250101T15");
        assertThat(response.price()).isEqualByComparingTo("45.50");
        assertThat(response.type()).isEqualTo(MarketType.BOND);
    }

    @Test
    void should_fallBackToIsinSearch_when_seriesCodeMissing() {
        // Arrange
        Bond b = bond("TRT999", "ISIN_X", BondType.DISCOUNTED, new BigDecimal("30"), null);
        when(bondRepository.findById("ISIN_X")).thenReturn(Optional.empty());
        Page<Bond> page = new PageImpl<>(List.of(b));
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        // Act
        MarketAssetResponse response = provider.getByCode("ISIN_X");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("TRT999");
        assertThat(response.price()).isEqualByComparingTo("30");
    }

    @Test
    void should_returnNull_when_bondNotFoundBySeriesCodeOrIsin() {
        // Arrange
        when(bondRepository.findById("MISSING")).thenReturn(Optional.empty());
        Page<Bond> emptyPage = new PageImpl<>(List.of());
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

        // Act
        MarketAssetResponse response = provider.getByCode("MISSING");

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void should_useCouponRateAsPrice_when_simpleYieldNull() {
        // Arrange
        Bond b = bond("TR1", null, BondType.FIXED_COUPON, null, new BigDecimal("8.5"));
        when(bondRepository.findById("TR1")).thenReturn(Optional.of(b));

        // Act
        MarketAssetResponse response = provider.getByCode("TR1");

        // Assert
        assertThat(response.price()).isEqualByComparingTo("8.5");
    }

    @Test
    void should_useBaseIndexAsPrice_when_present() {
        // Arrange
        Bond b = bond("TR2", "ISIN2", BondType.FIXED_COUPON, new BigDecimal("45.50"), new BigDecimal("10"));
        b.setBaseIndex(new BigDecimal("98.40"));
        when(bondRepository.findById("TR2")).thenReturn(Optional.of(b));

        // Act
        MarketAssetResponse response = provider.getByCode("TR2");

        // Assert — the clean price (baseIndex) wins over the yield/coupon fallback
        assertThat(response.price()).isEqualByComparingTo("98.40");
    }

    @Test
    void should_useSeriesCodeAsName_when_bondTypeIsNull() {
        // Arrange
        Bond b = bond("TR2", null, null, new BigDecimal("20"), null);
        when(bondRepository.findById("TR2")).thenReturn(Optional.of(b));

        // Act
        MarketAssetResponse response = provider.getByCode("TR2");

        // Assert
        assertThat(response.name()).isEqualTo("TR2");
    }

    @Test
    void should_returnSearchResults_when_searchHasTermAndFilters() {
        // Arrange
        Bond b = bond("S1", "I1", BondType.DISCOUNTED, new BigDecimal("12"), null);
        Page<Bond> page = new PageImpl<>(List.of(b));
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        // Act
        List<MarketAssetResponse> result = provider.search("S1",
                new MarketAssetFilters(null, "DISCOUNTED"), "price", "desc", 0, 10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("S1");
    }

    @Test
    void should_returnEmpty_when_searchTermAndPageEmpty() {
        // Arrange
        Page<Bond> emptyPage = new PageImpl<>(List.of());
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

        // Act
        List<MarketAssetResponse> result = provider.search("anything", MarketAssetFilters.none(),
                "name", "asc", 0, 5);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_ignoreInvalidSubType_when_searchFilterUnparseable() {
        // Arrange
        Page<Bond> emptyPage = new PageImpl<>(List.of());
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

        // Act
        List<MarketAssetResponse> result = provider.search(null,
                new MarketAssetFilters(null, "NONSENSE_TYPE"), "default", "desc", 0, 10);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_passThroughBlankSubType_when_filterPresentButBlank() {
        // Arrange
        Page<Bond> emptyPage = new PageImpl<>(List.of());
        when(bondRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

        // Act
        List<MarketAssetResponse> result = provider.search(null,
                new MarketAssetFilters(null, "   "), "default", "desc", 0, 10);

        // Assert
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "true,DESC",
            "false,ASC"
    })
    void should_orderBySimpleYieldDirection_when_topMovers(boolean gainers, String expectedDirection) {
        // Arrange
        Bond b = bond("S1", "I1", BondType.FIXED_COUPON, new BigDecimal("50"), null);
        Page<Bond> page = new PageImpl<>(List.of(b));
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        when(bondRepository.findAll(any(Specification.class), captor.capture())).thenReturn(page);

        // Act
        List<MarketAssetResponse> result = provider.getTopMovers(5, gainers);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(captor.getValue().getSort().getOrderFor("simpleYield"))
                .isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("simpleYield").getDirection().name())
                .isEqualTo(expectedDirection);
    }

    @Test
    void should_returnCount_when_countWithNullFilters() {
        // Arrange
        when(bondRepository.count(any(Specification.class))).thenReturn(7L);

        // Act
        long count = provider.count(null);

        // Assert
        assertThat(count).isEqualTo(7L);
    }

    @Test
    void should_returnCount_when_countWithValidSubType() {
        // Arrange
        when(bondRepository.count(any(Specification.class))).thenReturn(3L);

        // Act
        long count = provider.count(new MarketAssetFilters(null, "DISCOUNTED"));

        // Assert
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void should_returnCount_when_countBySearchWithBlankTerm() {
        // Arrange
        when(bondRepository.count(any(Specification.class))).thenReturn(5L);

        // Act
        long count = provider.countBySearch("  ", MarketAssetFilters.none());

        // Assert
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void should_returnCount_when_countBySearchWithTermAndSubType() {
        // Arrange
        when(bondRepository.count(any(Specification.class))).thenReturn(2L);

        // Act
        long count = provider.countBySearch("TR", new MarketAssetFilters(null, "FIXED_COUPON"));

        // Assert
        assertThat(count).isEqualTo(2L);
    }
}
