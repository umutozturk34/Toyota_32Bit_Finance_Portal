package com.finance.portfolio.fixedincome.bond;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondValuationServiceTest {

    private static final String ISIN = "TRT080631T19";
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 14);

    @Mock private BondRateHistoryRepository bondRateHistoryRepository;
    @Mock private BondRepository bondRepository;

    private BondValuationService service;

    @BeforeEach
    void setUp() {
        service = new BondValuationService(bondRateHistoryRepository, bondRepository);
    }

    private BondHolding holding(BigDecimal entryPrice, BigDecimal quantity) {
        return BondHolding.builder()
                .bondSeriesCode("TRT080631")
                .bondIsin(ISIN)
                .entryPrice(entryPrice)
                .quantity(quantity)
                .entryDate(LocalDate.of(2026, 1, 2))
                .build();
    }

    private void seedPrice(BigDecimal price) {
        when(bondRateHistoryRepository
                .findFirstByIsinCodeAndRateDateLessThanEqualOrderByRateDateDesc(eq(ISIN), eq(AS_OF)))
                .thenReturn(Optional.of(BondRateHistory.builder().price(price).build()));
    }

    private void seedNoPrice() {
        when(bondRateHistoryRepository
                .findFirstByIsinCodeAndRateDateLessThanEqualOrderByRateDateDesc(eq(ISIN), eq(AS_OF)))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldReturnLatestStoredPrice_whenRowAtOrBeforeAsOfExists() {
        // Arrange: a forward-filled clean price exists with rate_date <= asOf.
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal("10000"));
        seedPrice(new BigDecimal("95.50"));

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert
        assertThat(price).isEqualByComparingTo("95.50");
    }

    @Test
    void shouldFallBackToEntryPrice_whenNoPriceRowExists() {
        // Arrange: no bond_rate_history row at or before asOf for the ISIN.
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal("10000"));
        seedNoPrice();

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert
        assertThat(price).isEqualByComparingTo("90.00");
    }

    @Test
    void shouldFallBackToEntryPrice_whenStoredRowHasNullPrice() {
        // Arrange: a row exists but its price column is null — must not fail valuation.
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal("10000"));
        when(bondRateHistoryRepository
                .findFirstByIsinCodeAndRateDateLessThanEqualOrderByRateDateDesc(eq(ISIN), eq(AS_OF)))
                .thenReturn(Optional.of(BondRateHistory.builder().price(null).build()));

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert
        assertThat(price).isEqualByComparingTo("90.00");
    }

    @Test
    void shouldFallBackToEntryPriceWithoutHittingRepo_whenIsinIsMissing() {
        // Arrange: a holding with no ISIN cannot be looked up; entry price is used directly.
        BondHolding h = BondHolding.builder()
                .bondSeriesCode("TRT080631")
                .bondIsin(null)
                .entryPrice(new BigDecimal("88.25"))
                .quantity(new BigDecimal("10000"))
                .entryDate(LocalDate.of(2026, 1, 2))
                .build();

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert
        assertThat(price).isEqualByComparingTo("88.25");
    }

    @Test
    void shouldComputeValueFromStoredPrice_whenRowExists() {
        // Arrange: price 95.50 × 10000 bonds (per unit) = 955000 TRY.
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal("10000"));
        seedPrice(new BigDecimal("95.50"));

        // Act
        BigDecimal value = service.currentValueTry(h, AS_OF);

        // Assert
        assertThat(value).isEqualByComparingTo("955000");
    }

    @Test
    void shouldComputeValueFromEntryPrice_whenNoRowExists() {
        // Arrange: fallback to entry price 90.00 × 10000 bonds = 900000 TRY.
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal("10000"));
        seedNoPrice();

        // Act
        BigDecimal value = service.currentValueTry(h, AS_OF);

        // Assert
        assertThat(value).isEqualByComparingTo("900000");
    }

    @ParameterizedTest(name = "price {0} x qty {1} (per unit) = {2} TRY")
    @CsvSource({
            "95.50, 10000, 955000",
            "100.00, 5000, 500000",
            "101.2500, 2000, 202500",
            "88.00, 12500, 1100000"
    })
    void shouldValuePerUnit_whenPriceSeeded(
            String price, String quantity, String expectedValue) {
        // Arrange
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal(quantity));
        seedPrice(new BigDecimal(price));

        // Act
        BigDecimal value = service.currentValueTry(h, AS_OF);

        // Assert
        assertThat(value).isEqualByComparingTo(expectedValue);
    }

    @Test
    void shouldNotLookUpRepository_whenIsinBlankForCurrentValue() {
        // Arrange: blank ISIN -> entry price path; stub is lenient since the repo is never queried.
        BondHolding h = BondHolding.builder()
                .bondSeriesCode("TRT080631")
                .bondIsin("   ")
                .entryPrice(new BigDecimal("92.00"))
                .quantity(new BigDecimal("10000"))
                .entryDate(LocalDate.of(2026, 1, 2))
                .build();
        lenient().when(bondRateHistoryRepository
                        .findFirstByIsinCodeAndRateDateLessThanEqualOrderByRateDateDesc(eq(ISIN), eq(AS_OF)))
                .thenReturn(Optional.of(BondRateHistory.builder().price(new BigDecimal("95.50")).build()));

        // Act
        BigDecimal value = service.currentValueTry(h, AS_OF);

        // Assert: 92.00 × 10000 bonds = 920000, proving the seeded 95.50 row was bypassed.
        assertThat(value).isEqualByComparingTo("920000");
    }

    private Bond discountBond(LocalDate maturityEnd) {
        return Bond.builder()
                .seriesCode("TRT080631").isinCode(ISIN)
                .bondType(BondType.DISCOUNTED)
                .maturityStart(LocalDate.of(2026, 1, 2))
                .maturityEnd(maturityEnd)
                .build();
    }

    @Test
    void shouldRedeemDiscountBillAtPar_onMaturity() {
        // Arrange: a zero-coupon discount bill bought at 75, maturing on the as-of date.
        BondHolding h = holding(new BigDecimal("75.00"), new BigDecimal("10000"));
        when(bondRepository.findById("TRT080631")).thenReturn(Optional.of(discountBond(AS_OF)));

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert: a discount bill redeems at par (100) on maturity — its whole return is the price reaching par.
        assertThat(price).isEqualByComparingTo("100");
    }

    @Test
    void shouldAccreteDiscountBillTowardPar_midLife_ignoringScrapedHistory() {
        // Arrange: bought at 75 on 2026-01-02, matures 2026-12-31; valued mid-life on 2026-06-14. The history
        // repo is deliberately NOT seeded — a discount bill is valued by deterministic pull-to-par accretion,
        // never the sparse scraped quote.
        BondHolding h = holding(new BigDecimal("75.00"), new BigDecimal("10000"));
        when(bondRepository.findById("TRT080631"))
                .thenReturn(Optional.of(discountBond(LocalDate.of(2026, 12, 31))));

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert: ~44.9% of the way from 75 to par → ~86.2, strictly inside (75, 100) — never a stale endpoint.
        assertThat(price).isGreaterThan(new BigDecimal("86.0")).isLessThan(new BigDecimal("86.5"));
    }

    private Bond bondOfType(BondType type, LocalDate maturityEnd) {
        return Bond.builder()
                .seriesCode("TRT080631").isinCode(ISIN)
                .bondType(type)
                .maturityStart(LocalDate.of(2026, 1, 2))
                .maturityEnd(maturityEnd)
                .build();
    }

    @Test
    void shouldRedeemNonCpiBondAtPar_afterMaturity_ignoringStaleQuote() {
        // Arrange: a FIXED-coupon bond matured on 2026-03-01; the as-of (2026-06-14) is past maturity and a stale
        // 95.50 quote still exists. At/after maturity the face is redeemed at par, so the quote must be ignored.
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal("10000"));
        when(bondRepository.findById("TRT080631"))
                .thenReturn(Optional.of(bondOfType(BondType.FIXED_COUPON, LocalDate.of(2026, 3, 1))));
        lenient().when(bondRateHistoryRepository
                        .findFirstByIsinCodeAndRateDateLessThanEqualOrderByRateDateDesc(eq(ISIN), eq(AS_OF)))
                .thenReturn(Optional.of(BondRateHistory.builder().price(new BigDecimal("95.50")).build()));

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert: settled at par (100), not the stale 95.50.
        assertThat(price).isEqualByComparingTo("100");
    }

    @Test
    void shouldRedeemCpiBondAtIndexedValue_afterMaturity_notPar() {
        // Arrange: a CPI-indexed bond past maturity — it redeems at its indexed reference value (the stored index
        // price ~6316), NOT par, so settlement keeps the history path for CPI bonds.
        BondHolding h = holding(new BigDecimal("90.00"), new BigDecimal("10000"));
        when(bondRepository.findById("TRT080631"))
                .thenReturn(Optional.of(bondOfType(BondType.FLOATING_CPI, LocalDate.of(2026, 3, 1))));
        seedPrice(new BigDecimal("6316.2477"));

        // Act
        BigDecimal price = service.cleanPriceTry(h, AS_OF);

        // Assert: indexed redemption value, not 100.
        assertThat(price).isEqualByComparingTo("6316.2477");
    }

    @Test
    void shouldReturnEntryPrice_forDiscountBill_beforeEntryDate() {
        // Arrange: as-of predates the 2026-01-02 entry — there is no accretion yet.
        BondHolding h = holding(new BigDecimal("75.00"), new BigDecimal("10000"));
        when(bondRepository.findById("TRT080631"))
                .thenReturn(Optional.of(discountBond(LocalDate.of(2026, 12, 31))));

        // Act
        BigDecimal price = service.cleanPriceTry(h, LocalDate.of(2025, 12, 1));

        // Assert: before entry the bill is worth exactly what was paid (75), not yet accreting.
        assertThat(price).isEqualByComparingTo("75.00");
    }
}
