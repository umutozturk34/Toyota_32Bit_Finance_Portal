package com.finance.portfolio.fixedincome.bond;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.request.BondHoldingRequest;
import com.finance.portfolio.dto.response.BondCouponScheduleEntry;
import com.finance.portfolio.dto.response.BondHoldingResponse;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioType;
import com.finance.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondHoldingServiceTest {

    private static final String USER_SUB = "user-1";
    private static final String OTHER_USER = "user-2";
    private static final Long PORTFOLIO_ID = 7L;
    private static final Long OTHER_PORTFOLIO_ID = 8L;
    private static final Long HOLDING_ID = 33L;
    private static final String SERIES = "TRT080631";
    private static final String ISIN = "TRT080631T19";

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private BondHoldingRepository bondHoldingRepository;
    @Mock private BondRepository bondRepository;
    @Mock private BondValuationService bondValuationService;
    @Mock private BondRateHistoryRepository bondRateHistoryRepository;

    private final PortfolioProperties portfolioProperties = new PortfolioProperties();
    private BondHoldingService service;

    @BeforeEach
    void setUp() {
        service = new BondHoldingService(portfolioRepository, bondHoldingRepository,
                bondRepository, bondValuationService, new BondCouponService(),
                bondRateHistoryRepository, portfolioProperties);
    }

    private Portfolio ownedPortfolio() {
        return Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB)
                .type(PortfolioType.FIXED).build();
    }

    private Bond bond(LocalDate maturityEnd) {
        return Bond.builder()
                .seriesCode(SERIES)
                .isinCode(ISIN)
                .name("Hazine Tahvili")
                .couponRate(new BigDecimal("12.5000"))
                .maturityStart(LocalDate.of(2026, 6, 8))
                .maturityEnd(maturityEnd)
                .nextCouponDate(LocalDate.of(2026, 12, 8))
                .bondType(BondType.FIXED_COUPON)
                .build();
    }

    private BondRateHistory rateRow(LocalDate date, BigDecimal couponRate) {
        return BondRateHistory.builder().rateDate(date).couponRate(couponRate).build();
    }

    private BondRateHistory priceRow(LocalDate date, BigDecimal price) {
        return BondRateHistory.builder().rateDate(date).price(price).build();
    }

    @Test
    void shouldBuildCouponSchedule_forCpiBond_onIndexedBase() {
        // Arrange: a CPI bond issued ~13 months ago, semi-annual, small REAL coupon 0.85. Two coupons have paid;
        // each is converted against the INDEXED value at its own date (price × qty ÷ 100), not the face — so the
        // schedule is non-empty with positive TRY amounts (previously CPI returned an empty schedule).
        LocalDate issue = LocalDate.now().minusMonths(13);
        Bond cpi = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("TÜFE")
                .couponRate(new BigDecimal("0.85"))
                .maturityStart(issue).maturityEnd(LocalDate.now().plusYears(3))
                .bondType(BondType.FLOATING_CPI).build();
        BondHolding held = BondHolding.builder()
                .id(HOLDING_ID).portfolio(Portfolio.builder().id(PORTFOLIO_ID).build()).portfolioId(PORTFOLIO_ID)
                .bondSeriesCode(SERIES).bondIsin(ISIN)
                .quantity(new BigDecimal("100")).entryPrice(new BigDecimal("6000")).entryDate(issue)
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(held));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(cpi));
        when(bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(ISIN)).thenReturn(List.of(
                priceRow(issue, new BigDecimal("6000")),
                priceRow(LocalDate.now(), new BigDecimal("6300"))));

        // Act
        List<BondCouponScheduleEntry> schedule = service.couponSchedule(PORTFOLIO_ID, HOLDING_ID, USER_SUB);

        // Assert: CPI now produces a schedule with positive TRY amounts on the indexed base.
        assertThat(schedule).isNotEmpty();
        assertThat(schedule).allSatisfy(e -> assertThat(e.amountTry()).isGreaterThan(BigDecimal.ZERO));
    }

    private BondHoldingRequest request(BigDecimal quantity, BigDecimal entryPrice, LocalDate entryDate) {
        return new BondHoldingRequest(SERIES, quantity, entryPrice, entryDate, null, null);
    }

    private BondHoldingRequest requestWithCoupon(BigDecimal couponRateOverride) {
        return new BondHoldingRequest(SERIES, new BigDecimal("10000"), new BigDecimal("95.00"),
                LocalDate.of(2026, 1, 2), couponRateOverride, null);
    }

    private BondHoldingRequest validRequest() {
        return request(new BigDecimal("10000"), new BigDecimal("95.00"), LocalDate.of(2026, 1, 2));
    }

    private BondHolding holding(Long portfolioId) {
        return BondHolding.builder()
                .id(HOLDING_ID)
                .portfolio(Portfolio.builder().id(portfolioId).build())
                .portfolioId(portfolioId)
                .bondSeriesCode(SERIES)
                .bondIsin(ISIN)
                .quantity(new BigDecimal("10000"))
                .entryPrice(new BigDecimal("95.00"))
                .entryDate(LocalDate.of(2026, 1, 2))
                .build();
    }

    @Test
    void shouldPersistHoldingWithDenormalizedIsin_whenPortfolioOwnedByUser() {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("98.00"));

        // Act
        BondHoldingResponse response = service.add(PORTFOLIO_ID, USER_SUB, validRequest());

        // Assert
        ArgumentCaptor<BondHolding> captor = ArgumentCaptor.forClass(BondHolding.class);
        verify(bondHoldingRepository).save(captor.capture());
        assertThat(captor.getValue().getBondSeriesCode()).isEqualTo(SERIES);
        assertThat(captor.getValue().getBondIsin()).isEqualTo(ISIN);
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10000");
        assertThat(response.bondIsin()).isEqualTo(ISIN);
        assertThat(response.bondName()).isEqualTo("Hazine Tahvili");
    }

    @ParameterizedTest(name = "owner={0} portfolioOwnedByCaller={1}")
    @CsvSource({
            "user-1, false",
            "user-2, false"
    })
    void shouldThrowResourceNotFound_whenPortfolioNotOwnedByCaller(String caller, boolean ignored) {
        // Arrange: the portfolio does not resolve for the calling user (cross-user / missing).
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, caller)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, caller, validRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("error.portfolio.notFound");
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenOtherUserAddsToForeignPortfolio() {
        // Arrange: portfolio belongs to USER_SUB; OTHER_USER's owner-scoped lookup returns empty.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, OTHER_USER)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, OTHER_USER, validRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bondRepository, never()).findById(any());
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectAdd_whenPortfolioIsSpotType() {
        // Arrange: caller owns the portfolio, but it is a SPOT portfolio — bonds may only go in a FIXED one.
        Portfolio spot = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB)
                .type(PortfolioType.SPOT).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(spot));

        // Act + Assert: the type gate fires before the bond is ever resolved.
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.notFixedType");
        verify(bondRepository, never()).findById(any());
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotInPortfolio_whenHoldingBelongsToAnotherPortfolio() {
        // Arrange: caller owns PORTFOLIO_ID, but the loaded holding is tagged to OTHER_PORTFOLIO_ID.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID))
                .thenReturn(Optional.of(holding(OTHER_PORTFOLIO_ID)));

        // Act + Assert
        assertThatThrownBy(() -> service.delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.position.notInPortfolio");
        verify(bondHoldingRepository, never()).delete(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenHoldingIdMissing() {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(404L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.delete(PORTFOLIO_ID, 404L, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("error.portfolio.position.notFound");
    }

    @Test
    void shouldThrowBondNotFound_whenSeriesCodeDoesNotResolve() {
        // Arrange: portfolio owned, but no bond exists for the series code.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.notFound");
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldThrowAlreadyMatured_whenEntryDateNotBeforeMaturityEnd() {
        // Arrange: bond matured before the entry date.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2020, 1, 1))));

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.alreadyMatured");
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldThrowEntryDateInFuture_whenEntryDateAfterToday() {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2099, 1, 1))));
        BondHoldingRequest future = request(new BigDecimal("10000"), new BigDecimal("95.00"),
                LocalDate.now().plusDays(1));

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, future))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.entryDateInFuture");
    }

    @Test
    void shouldCloseHoldingWithExit_whenSelling() {
        // Arrange
        BondHolding open = holding(PORTFOLIO_ID);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(open));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: exit at 101.00 per 100 nominal on a valid date.
        BondHoldingResponse response = service.sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB,
                LocalDate.of(2026, 6, 1), new BigDecimal("101.00"));

        // Assert: holding closed and realized at the exit price (101 × 10000 bonds, per unit = 1010000 TRY).
        assertThat(open.isClosed()).isTrue();
        assertThat(open.getExitPrice()).isEqualByComparingTo("101.00");
        assertThat(response.exitDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.currentValueTry()).isEqualByComparingTo("1010000");
    }

    @Test
    void shouldRejectSell_whenAlreadyClosed() {
        // Arrange
        BondHolding closed = holding(PORTFOLIO_ID);
        closed.closeWith(LocalDate.of(2026, 3, 1), new BigDecimal("99.00"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(closed));

        // Act + Assert
        assertThatThrownBy(() -> service.sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB,
                LocalDate.of(2026, 6, 1), new BigDecimal("101.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.alreadyClosed");
    }

    @ParameterizedTest(name = "exitPrice={0} -> {1}")
    @CsvSource({
            "-5,     error.portfolio.bond.priceTooLow",
            "0,      error.portfolio.bond.priceTooLow",
            "200000, error.portfolio.bond.priceTooHigh"
    })
    void shouldRejectSell_whenExitPriceOutOfBounds(String exitPrice, String expectedKey) {
        // Arrange: an open holding, a valid exit date, but an out-of-bounds exit price (direct-API bypass).
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding(PORTFOLIO_ID)));

        // Act + Assert: the sell path now enforces the same TRY clean-price bounds as add/update.
        assertThatThrownBy(() -> service.sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB,
                LocalDate.of(2026, 6, 1), new BigDecimal(exitPrice)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(expectedKey);
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdate_whenSeriesCodeChanged() {
        // Arrange: the held series is SERIES; the update request carries a DIFFERENT series code.
        BondHolding owned = holding(PORTFOLIO_ID);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(owned));
        BondHoldingRequest swapped = new BondHoldingRequest("TRT999999X99", new BigDecimal("5000"),
                new BigDecimal("97.50"), LocalDate.of(2026, 2, 1), null, null);

        // Act + Assert: the series/ISIN is immutable after add, so a swap is rejected before bond resolution.
        assertThatThrownBy(() -> service.update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, swapped))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.seriesImmutable");
        verify(bondRepository, never()).findById(any());
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectSell_whenExitBeforeEntry() {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding(PORTFOLIO_ID)));

        // Act + Assert: entry is 2026-01-02, exit dated before it.
        assertThatThrownBy(() -> service.sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB,
                LocalDate.of(2025, 12, 1), new BigDecimal("101.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.exitBeforeEntry");
    }

    @Test
    void shouldRejectSell_whenExitAfterMaturity() {
        // Arrange: an open holding whose bond matured on 2026-03-01, with a requested exit AFTER that date.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding(PORTFOLIO_ID)));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2026, 3, 1))));

        // Act + Assert: past maturity a bond is redeemed at par, not sold — the server rejects a post-maturity exit.
        assertThatThrownBy(() -> service.sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB,
                LocalDate.of(2026, 6, 1), new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.exitAfterMaturity");
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldReopenClosedHolding_clearingExit() {
        // Arrange
        BondHolding closed = holding(PORTFOLIO_ID);
        closed.closeWith(LocalDate.of(2026, 3, 1), new BigDecimal("99.00"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(closed));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("97.00"));

        // Act
        BondHoldingResponse response = service.reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB);

        // Assert
        assertThat(closed.isClosed()).isFalse();
        assertThat(response.exitDate()).isNull();
        assertThat(response.exitPrice()).isNull();
    }

    @Test
    void shouldRejectReopen_whenNotClosed() {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding(PORTFOLIO_ID)));

        // Act + Assert
        assertThatThrownBy(() -> service.reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.notClosed");
    }

    @Test
    void shouldComputeValueAndPnlFromValuationService_whenListingOpenHolding() {
        // Arrange: clean price 98.00 × 10000 / 100 = 9800 value; cost 95 × 10000 / 100 = 9500; pnl 300.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID))
                .thenReturn(List.of(holding(PORTFOLIO_ID)));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("98.00"));

        // Act
        List<BondHoldingResponse> result = service.list(PORTFOLIO_ID, USER_SUB);

        // Assert
        assertThat(result).hasSize(1);
        BondHoldingResponse row = result.get(0);
        assertThat(row.currentPriceTry()).isEqualByComparingTo("98.00");
        assertThat(row.currentValueTry()).isEqualByComparingTo("980000");
        assertThat(row.costTry()).isEqualByComparingTo("950000");
        assertThat(row.pnlTry()).isEqualByComparingTo("30000");
        assertThat(row.pnlPercent()).isEqualByComparingTo("3.15789474");
        // The coupon/maturity block is denormalized from the resolved market bond for the grid + chart.
        // couponRate is the EFFECTIVE ANNUAL rate: the bond publishes 12.5000 SEMI-ANNUAL, annualized × 2 = 25.0.
        assertThat(row.couponRate()).isEqualByComparingTo("25.0000");
        assertThat(row.publishedCouponRate()).isEqualByComparingTo("25.0000");
        assertThat(row.maturityStart()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(row.maturityEnd()).isEqualTo(LocalDate.of(2031, 6, 8));
        assertThat(row.nextCouponDate()).isEqualTo(LocalDate.of(2026, 12, 8));
        assertThat(row.bondType()).isEqualTo("FIXED_COUPON");
        assertThat(row.couponFrequency()).isEqualTo("SEMI_ANNUAL");
    }

    @Test
    void shouldLeaveCouponBlockNull_whenSeriesNoLongerResolves() {
        // Arrange: the holding exists but its series no longer resolves to a market bond (delisted/expired
        // catalog entry). The row must still render — valuation does not depend on the catalog lookup.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID))
                .thenReturn(List.of(holding(PORTFOLIO_ID)));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.empty());
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("98.00"));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: valuation still computed; the entire reference block (incl. name) is null, never thrown.
        assertThat(row.currentValueTry()).isEqualByComparingTo("980000");
        assertThat(row.bondName()).isNull();
        assertThat(row.couponRate()).isNull();
        assertThat(row.maturityStart()).isNull();
        assertThat(row.maturityEnd()).isNull();
        assertThat(row.nextCouponDate()).isNull();
        assertThat(row.bondType()).isNull();
        // couponFrequency is a HOLDING property (default SEMI_ANNUAL), not catalog-dependent, so it survives
        // even when the series no longer resolves.
        assertThat(row.couponFrequency()).isEqualTo("SEMI_ANNUAL");
    }

    @ParameterizedTest(name = "quantity={0} -> {1}")
    @CsvSource({
            "0.0000000001, error.portfolio.bond.quantityTooLow",
            "2000000000,   error.portfolio.bond.quantityTooHigh"
    })
    void shouldRejectOutOfBoundsQuantity(String quantity, String expectedKey) {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        BondHoldingRequest req = request(new BigDecimal(quantity), new BigDecimal("95.00"),
                LocalDate.of(2026, 1, 2));

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessage(expectedKey);
        verify(bondHoldingRepository, never()).save(any());
    }

    @ParameterizedTest(name = "price={0} -> {1}")
    @CsvSource({
            "0.00001, error.portfolio.bond.priceTooLow",
            "200000,  error.portfolio.bond.priceTooHigh"
    })
    void shouldRejectOutOfBoundsPrice(String price, String expectedKey) {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        BondHoldingRequest req = request(new BigDecimal("10000"), new BigDecimal(price),
                LocalDate.of(2026, 1, 2));

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessage(expectedKey);
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectEntryDateBeforeMinimum() {
        // Arrange
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        BondHoldingRequest req = request(new BigDecimal("10000"), new BigDecimal("95.00"),
                LocalDate.of(1999, 1, 1));

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.entryDateTooOld");
    }

    @Test
    void shouldDeleteOwnedHolding() {
        // Arrange
        BondHolding owned = holding(PORTFOLIO_ID);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(owned));

        // Act
        service.delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB);

        // Assert
        verify(bondHoldingRepository).delete(owned);
    }

    @Test
    void shouldUpdateOwnedHoldingFields() {
        // Arrange
        BondHolding owned = holding(PORTFOLIO_ID);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(owned));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("96.00"));
        BondHoldingRequest req = request(new BigDecimal("5000"), new BigDecimal("97.50"),
                LocalDate.of(2026, 2, 1));

        // Act
        BondHoldingResponse response = service.update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, req);

        // Assert
        assertThat(owned.getQuantity()).isEqualByComparingTo("5000");
        assertThat(owned.getEntryPrice()).isEqualByComparingTo("97.50");
        assertThat(owned.getEntryDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(response.quantity()).isEqualByComparingTo("5000");
    }

    @Test
    void shouldPersistCouponOverride_andReportEffectiveAndRaw_whenAddingWithOverride() {
        // Arrange: the holder overwrites the bond's published 12.5000 coupon with 15.0000.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("98.00"));

        // Act
        BondHoldingResponse response = service.add(PORTFOLIO_ID, USER_SUB,
                requestWithCoupon(new BigDecimal("15.0000")));

        // Assert: the override persists on the holding, and the response surfaces it as the effective coupon
        // (winning over the bond's 12.5000) plus the raw stored value and the convenience flag.
        ArgumentCaptor<BondHolding> captor = ArgumentCaptor.forClass(BondHolding.class);
        verify(bondHoldingRepository).save(captor.capture());
        assertThat(captor.getValue().getCouponRateOverride()).isEqualByComparingTo("15.0000");
        assertThat(response.couponRate()).isEqualByComparingTo("15.0000");
        assertThat(response.couponRateOverride()).isEqualByComparingTo("15.0000");
        assertThat(response.couponOverridden()).isTrue();
    }

    @Test
    void shouldReportPublishedCoupon_andNullRaw_whenAddingWithoutOverride() {
        // Arrange: no override supplied — the response must fall back to the bond's published coupon.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("98.00"));

        // Act
        BondHoldingResponse response = service.add(PORTFOLIO_ID, USER_SUB, requestWithCoupon(null));

        // Assert: effective coupon = the bond's published rate annualized (12.5000 × 2 = 25.0); raw override null.
        assertThat(response.couponRate()).isEqualByComparingTo("25.0000");
        assertThat(response.couponRateOverride()).isNull();
        assertThat(response.couponOverridden()).isFalse();
    }

    @Test
    void shouldSurfaceOverride_evenWhenSeriesNoLongerResolves() {
        // Arrange: the holding carries an override but its series no longer resolves to a market bond. The
        // override must still win as the effective coupon since it does not depend on the catalog lookup.
        BondHolding overridden = holding(PORTFOLIO_ID);
        overridden.update(null, null, null, new BigDecimal("9.7500"), null);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID))
                .thenReturn(List.of(overridden));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.empty());
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("98.00"));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: effective coupon = the override; the name/maturity block stays null (series unresolved).
        assertThat(row.couponRate()).isEqualByComparingTo("9.7500");
        assertThat(row.couponRateOverride()).isEqualByComparingTo("9.7500");
        assertThat(row.couponOverridden()).isTrue();
        assertThat(row.bondName()).isNull();
    }

    @Test
    void shouldClearCouponOverride_whenUpdatingWithNull() {
        // Arrange: the holding already carries an override; the update passes a null coupon to revert it.
        BondHolding owned = holding(PORTFOLIO_ID);
        owned.update(null, null, null, new BigDecimal("20.0000"), null);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(owned));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("96.00"));

        // Act: a null override on update CLEARS the prior override, reverting to the published coupon.
        BondHoldingResponse response = service.update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, requestWithCoupon(null));

        // Assert: clearing the override reverts to the published rate annualized (12.5000 × 2 = 25.0).
        assertThat(owned.getCouponRateOverride()).isNull();
        assertThat(response.couponRate()).isEqualByComparingTo("25.0000");
        assertThat(response.couponOverridden()).isFalse();
    }

    @Test
    void shouldRejectCouponOverride_whenAboveMaximum() {
        // Arrange: the override (200) exceeds the configured BondLimits maxCouponRate (100).
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));

        // Act + Assert
        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB,
                requestWithCoupon(new BigDecimal("200"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.bond.couponOutOfRange");
        verify(bondHoldingRepository, never()).save(any());
    }

    @Test
    void shouldAcceptZeroCouponOverride_forZeroCouponBono() {
        // Arrange: a zero-coupon bill (bono) is legitimately entered with a 0 override.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(bond(LocalDate.of(2031, 6, 8))));
        when(bondHoldingRepository.save(any(BondHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("98.00"));

        // Act
        BondHoldingResponse response = service.add(PORTFOLIO_ID, USER_SUB,
                requestWithCoupon(BigDecimal.ZERO));

        // Assert: zero is within bounds, so it persists as the effective (overridden) coupon.
        assertThat(response.couponRate()).isEqualByComparingTo("0");
        assertThat(response.couponOverridden()).isTrue();
    }

    @Test
    void shouldAccrueRealCouponOnIndexedPrincipal_forCpiBond() {
        // Arrange: a CPI-indexed bond whose stored price is the cumulative inflation index (~6300), NOT a per-100
        // clean price. Its return is the indexation (captured by that price) PLUS a low real coupon paid on the
        // INDEXED principal — so the real coupon must accrue scaled onto the indexed value (nominalValue), not the
        // per-100 face (accruing on face would understate it ~60×). maturityStart is 4 months ago so we sit ~2/3
        // into the current semi-annual coupon period, guaranteeing a positive accrued slice regardless of run date.
        Bond cpi = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("TÜFE'ye Endeksli")
                .couponRate(new BigDecimal("0.8500"))
                .maturityStart(LocalDate.now().minusMonths(4))
                .maturityEnd(LocalDate.now().plusYears(5))
                .bondType(BondType.FLOATING_CPI)
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID))
                .thenReturn(List.of(holding(PORTFOLIO_ID)));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(cpi));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("6316.2477"));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: value = 6316.2477 × 10000 bonds (per unit) = 63162477 (CLEAN — accrued is a separate line).
        // The accrued real coupon is POSITIVE and clearly rides the indexed principal, scaling on the index value
        // rather than the plain face.
        assertThat(row.nominalValueTry()).isEqualByComparingTo("63162477");
        assertThat(row.currentValueTry()).isEqualByComparingTo("63162477");
        assertThat(row.accruedCouponTry()).isGreaterThan(new BigDecimal("100"));
        assertThat(row.dailyCouponAccrualTry()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldReportRealizedCoupons_forBackDatedEntry() {
        // Arrange: a fixed-coupon bond issued and entered ~13 months ago. With a semi-annual coupon, two coupon
        // dates (≈6mo, ≈12mo after issue) have already PAID, so the back-dated holder has realized coupon income —
        // counted from the entry date onward even though the purchase was back-dated.
        LocalDate issue = LocalDate.now().minusMonths(13);
        Bond fixed = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("Sabit Kupon")
                .couponRate(new BigDecimal("10.0000"))
                .maturityStart(issue)
                .maturityEnd(LocalDate.now().plusYears(3))
                .bondType(BondType.FIXED_COUPON)
                .build();
        BondHolding backDated = BondHolding.builder()
                .id(HOLDING_ID)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .portfolioId(PORTFOLIO_ID)
                .bondSeriesCode(SERIES).bondIsin(ISIN)
                .quantity(new BigDecimal("10000"))
                .entryPrice(new BigDecimal("95.00"))
                .entryDate(issue)
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(backDated));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(fixed));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("99.00"));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: two semi-annual coupons paid in the 13-month holding window.
        assertThat(row.couponsReceivedCount()).isGreaterThanOrEqualTo(2);
        assertThat(row.couponsReceivedTry()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldRealizeAccruedCouponAtExit_forClosedHolding() {
        // Arrange: a fixed-coupon bond issued ~2 months ago, semi-annual — still inside the FIRST coupon period
        // (no full coupon paid yet). The holder sells today, so the işlemiş kupon accrued to the exit date is
        // realized in the dirty sale proceeds and must surface as accrued income — NOT zeroed like a clean close.
        LocalDate issue = LocalDate.now().minusMonths(2);
        Bond fixed = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("Sabit Kupon")
                .couponRate(new BigDecimal("10.0000"))
                .maturityStart(issue)
                .maturityEnd(LocalDate.now().plusYears(3))
                .bondType(BondType.FIXED_COUPON)
                .build();
        BondHolding closed = BondHolding.builder()
                .id(HOLDING_ID).portfolio(Portfolio.builder().id(PORTFOLIO_ID).build()).portfolioId(PORTFOLIO_ID)
                .bondSeriesCode(SERIES).bondIsin(ISIN)
                .quantity(new BigDecimal("10000")).entryPrice(new BigDecimal("100")).entryDate(issue)
                .build();
        closed.closeWith(LocalDate.now(), new BigDecimal("101.00"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(closed));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(fixed));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: accrued realized at the sale (dirty proceeds), but no further daily accrual after exit.
        assertThat(row.accruedCouponTry()).isGreaterThan(BigDecimal.ZERO);
        assertThat(row.dailyCouponAccrualTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldFlagRedeemed_whenNonCpiBondMaturedWhileStillOpen() {
        // Arrange: a fixed-coupon bond whose maturity is already in the past, still held (never sold). The issuer
        // has repaid par, so the open holding is auto-settled as REDEEMED — presented as closed-by-redemption.
        LocalDate issue = LocalDate.now().minusYears(2);
        Bond matured = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("Vadesi Dolmuş")
                .couponRate(new BigDecimal("10.0000"))
                .maturityStart(issue)
                .maturityEnd(LocalDate.now().minusDays(5))
                .bondType(BondType.FIXED_COUPON)
                .build();
        BondHolding held = BondHolding.builder()
                .id(HOLDING_ID).portfolio(Portfolio.builder().id(PORTFOLIO_ID).build()).portfolioId(PORTFOLIO_ID)
                .bondSeriesCode(SERIES).bondIsin(ISIN)
                .quantity(new BigDecimal("10000")).entryPrice(new BigDecimal("95")).entryDate(issue)
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(held));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(matured));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("100"));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: matured-while-open → auto-redeemed.
        assertThat(row.redeemed()).isTrue();
    }

    @Test
    void shouldValueGoldBondPerUnit_notPer100() {
        // Arrange: a gold-linked (altına dayalı) bond is quoted PER CERTIFICATE, so value = price × quantity and
        // cost = entryPrice × quantity — NOT divided by 100 like a per-100 nominal bond.
        Bond gold = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("Altın")
                .couponRate(new BigDecimal("0.40"))
                .maturityStart(LocalDate.now().minusMonths(3))
                .maturityEnd(LocalDate.now().plusYears(1))
                .bondType(BondType.GOLD)
                .build();
        BondHolding holding = BondHolding.builder()
                .id(HOLDING_ID).portfolio(Portfolio.builder().id(PORTFOLIO_ID).build()).portfolioId(PORTFOLIO_ID)
                .bondSeriesCode(SERIES).bondIsin(ISIN)
                .quantity(new BigDecimal("10")).entryPrice(new BigDecimal("6000"))
                .entryDate(LocalDate.now().minusMonths(1))
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(holding));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(gold));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("6287"));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: 6287 × 10 (per certificate), cost 6000 × 10 — not ÷ 100.
        assertThat(row.currentValueTry()).isEqualByComparingTo("62870");
        assertThat(row.costTry()).isEqualByComparingTo("60000");
    }

    @Test
    void shouldAccrueCoupon_forTlrefFloater_likeAFixedBond() {
        // Arrange: a TLREF floater — its price orbits par (~100), so the (reset) coupon IS the return and must
        // accrue, UNLIKE a CPI-indexed bond whose price is the inflation index. maturityStart is 2 months ago so
        // there is a positive accrued slice in the current coupon period.
        Bond tlref = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("TLREF Değişken")
                .couponRate(new BigDecimal("9.9700"))
                .maturityStart(LocalDate.now().minusMonths(2))
                .maturityEnd(LocalDate.now().plusYears(2))
                .bondType(BondType.FLOATING_TLREF)
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID))
                .thenReturn(List.of(holding(PORTFOLIO_ID)));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(tlref));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("104.50"));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: a TLREF floater accrues its coupon (gate is CPI-only), so accrued coupon is positive.
        assertThat(row.accruedCouponTry()).isGreaterThan(BigDecimal.ZERO);
        assertThat(row.dailyCouponAccrualTry()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldUsePerPeriodRateFromHistory_forTlrefFloater() {
        // Arrange: a TLREF floater issued 14 months ago, semi-annual, so two coupons have already paid (~6mo and
        // ~12mo after issue). Its coupon-rate history RESETS between them: 10.00 for the first period, 14.00 for the
        // second. Realized coupons must sum each period AT ITS OWN reset rate (10 + 14), not one flat rate.
        LocalDate issue = LocalDate.now().minusMonths(14);
        Bond tlref = Bond.builder()
                .seriesCode(SERIES).isinCode(ISIN).name("TLREF Değişken")
                .couponRate(new BigDecimal("14.0000"))
                .maturityStart(issue)
                .maturityEnd(LocalDate.now().plusYears(2))
                .bondType(BondType.FLOATING_TLREF)
                .build();
        BondHolding backDated = BondHolding.builder()
                .id(HOLDING_ID).portfolio(Portfolio.builder().id(PORTFOLIO_ID).build()).portfolioId(PORTFOLIO_ID)
                .bondSeriesCode(SERIES).bondIsin(ISIN)
                .quantity(new BigDecimal("100")).entryPrice(new BigDecimal("100")).entryDate(issue)
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(backDated));
        when(bondRepository.findById(SERIES)).thenReturn(Optional.of(tlref));
        when(bondValuationService.cleanPriceTry(any(BondHolding.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("101.00"));
        // Reset history: 10.00 from issue, stepping to 14.00 from ~7 months in (i.e. after the first coupon).
        when(bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(ISIN)).thenReturn(List.of(
                rateRow(issue, new BigDecimal("10.0000")),
                rateRow(issue.plusMonths(7), new BigDecimal("14.0000"))));

        // Act
        BondHoldingResponse row = service.list(PORTFOLIO_ID, USER_SUB).get(0);

        // Assert: 2 coupons, summed at their own period rates (10 + 14 = 24 per 100 → × 100 nominal ÷ 100 = 24 TRY).
        assertThat(row.couponsReceivedCount()).isEqualTo(2);
        assertThat(row.couponsReceivedTry()).isEqualByComparingTo("2400.0000");
    }

    @Test
    void shouldAnnualizePublishedRate_whenEffectiveCouponHasNoOverride() {
        // Arrange: a holding with no override; the effective ANNUAL coupon annualizes the bond's published
        // SEMI-ANNUAL rate (× 2), and a set override (already annual) wins verbatim.
        BondHolding noOverride = holding(PORTFOLIO_ID);

        // Act + Assert: 11.2500 semi-annual → 22.5000 annual.
        assertThat(noOverride.effectiveAnnualCouponRate(new BigDecimal("11.2500")))
                .isEqualByComparingTo("22.5000");
        // And the (annual) override wins once set.
        noOverride.update(null, null, null, new BigDecimal("13.0000"), null);
        assertThat(noOverride.effectiveAnnualCouponRate(new BigDecimal("11.2500")))
                .isEqualByComparingTo("13.0000");
    }

    @Test
    void shouldAnnualizePublishedRateByFrequency_notAlwaysByTwo() {
        // Arrange: a QUARTERLY holding annualizes the published PER-PERIOD coupon × 4 (not a blanket × 2), so
        // couponPerPeriod stays == published at any cadence — this is the fix for the TLREF/quarterly under-statement.
        BondHolding quarterly = BondHolding.builder()
                .bondSeriesCode(SERIES).bondIsin(ISIN).quantity(new BigDecimal("1000"))
                .entryPrice(new BigDecimal("100")).entryDate(LocalDate.of(2026, 1, 2))
                .couponPaymentFrequency(CouponFrequency.QUARTERLY)
                .build();

        // Act + Assert: 5.00 per period × 4 quarters = 20.00 annual (a semi-annual holding would give × 2 = 10.00).
        assertThat(quarterly.effectiveAnnualCouponRate(new BigDecimal("5.0000")))
                .isEqualByComparingTo("20.0000");
    }

    @Test
    void shouldNotTrustHoldingId_whenPortfolioLookupFailsFirst() {
        // Arrange: ownership guard must run before any holding load, even for a holding-targeting op.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());
        lenient().when(bondHoldingRepository.findById(anyLong()))
                .thenReturn(Optional.of(holding(PORTFOLIO_ID)));

        // Act + Assert
        assertThatThrownBy(() -> service.delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bondHoldingRepository, never()).findById(eq(HOLDING_ID));
    }
}
