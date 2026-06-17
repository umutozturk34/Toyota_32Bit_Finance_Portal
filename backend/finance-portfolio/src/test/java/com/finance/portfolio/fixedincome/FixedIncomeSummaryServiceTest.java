package com.finance.portfolio.fixedincome;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Currency;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.FxRateUnavailableException;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.response.BondHoldingResponse;
import com.finance.portfolio.dto.response.DepositHoldingResponse;
import com.finance.portfolio.dto.response.FixedIncomeHistoryPoint;
import com.finance.portfolio.dto.response.FixedIncomeSummaryResponse;
import com.finance.portfolio.fixedincome.bond.BondCouponEventBuilder;
import com.finance.portfolio.fixedincome.bond.BondCouponService;
import com.finance.portfolio.fixedincome.bond.BondHolding;
import com.finance.portfolio.fixedincome.bond.BondHoldingRepository;
import com.finance.portfolio.fixedincome.bond.BondHoldingService;
import com.finance.portfolio.fixedincome.bond.BondValuationService;
import com.finance.portfolio.fixedincome.deposit.DepositAccrualService;
import com.finance.portfolio.fixedincome.deposit.DepositHolding;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingRepository;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingService;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FixedIncomeSummaryService}: the headline totals/allocation math over a mix of
 * active + closed deposits and bonds, the non-negotiable userSub ownership guard on BOTH entry points, and
 * the history series zeroing a holding before it comes online. Plain Mockito + AAA; the valuation services
 * and FX converter are stubbed so the tests assert the service's aggregation, not the underlying math.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedIncomeSummaryServiceTest {

    private static final String USER_SUB = "user-1";
    private static final Long PORTFOLIO_ID = 7L;

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private DepositHoldingRepository depositHoldingRepository;
    @Mock private BondHoldingRepository bondHoldingRepository;
    @Mock private DepositAccrualService depositAccrualService;
    @Mock private BondValuationService bondValuationService;
    @Mock private BondRateHistoryRepository bondRateHistoryRepository;
    @Mock private CurrencyConverter currencyConverter;
    @Mock private BondRepository bondRepository;

    private final PortfolioProperties portfolioProperties = new PortfolioProperties();
    private FixedIncomeSummaryService service;
    private DepositHoldingService depositGridService;
    private BondHoldingService bondGridService;

    @BeforeEach
    void setUp() {
        service = new FixedIncomeSummaryService(portfolioRepository, depositHoldingRepository,
                bondHoldingRepository, depositAccrualService, bondValuationService,
                new BondCouponEventBuilder(new BondCouponService(), bondRateHistoryRepository),
                bondRateHistoryRepository, bondRepository, currencyConverter);
        // The per-row grid services share the SAME mocks as the summary service, so a single arrange-once book
        // can be valued through all three surfaces and asserted to reconcile.
        depositGridService = new DepositHoldingService(portfolioRepository, depositHoldingRepository,
                depositAccrualService, currencyConverter);
        bondGridService = new BondHoldingService(portfolioRepository, bondHoldingRepository,
                bondRepository, bondValuationService, new BondCouponService(),
                bondRateHistoryRepository, portfolioProperties);
    }

    private void ownsPortfolio() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB))
                .thenReturn(Optional.of(Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build()));
    }

    private DepositHolding tryDeposit(long id, BigDecimal principal, LocalDate start) {
        return DepositHolding.builder()
                .id(id)
                .currency("TRY")
                .principal(principal)
                .annualRate(new BigDecimal("45"))
                .startDate(start)
                .maturityDate(start.plusYears(1))
                .build();
    }

    private DepositHolding closedTryDeposit(long id, BigDecimal principal, LocalDate start,
                                            BigDecimal closedValueTry) {
        DepositHolding deposit = tryDeposit(id, principal, start);
        deposit.close(start.plusMonths(6), closedValueTry);
        return deposit;
    }

    private BondHolding bond(long id, BigDecimal quantity, BigDecimal entryPrice, LocalDate entryDate) {
        return BondHolding.builder()
                .id(id)
                .bondSeriesCode("TRT080626T17")
                .bondIsin("TRT080626T17")
                .quantity(quantity)
                .entryPrice(entryPrice)
                .entryDate(entryDate)
                .build();
    }

    private BondHolding closedBond(long id, BigDecimal quantity, BigDecimal entryPrice, LocalDate entryDate,
                                   LocalDate exitDate, BigDecimal exitPrice) {
        BondHolding bond = bond(id, quantity, entryPrice, entryDate);
        bond.closeWith(exitDate, exitPrice);
        return bond;
    }

    private BondRateHistory rateRow(LocalDate date, BigDecimal price) {
        return BondRateHistory.builder().rateDate(date).price(price).build();
    }

    private DepositHolding usdDeposit(long id, BigDecimal principal, LocalDate start) {
        return DepositHolding.builder()
                .id(id)
                .currency("USD")
                .principal(principal)
                .annualRate(new BigDecimal("10"))
                .startDate(start)
                .maturityDate(start.plusYears(1))
                .build();
    }

    // ---- ownership guard (the non-negotiable) ------------------------------------------------------------

    @Test
    void shouldThrowResourceNotFound_whenSummaryPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summary(PORTFOLIO_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
        verify(depositHoldingRepository, never()).findByPortfolioIdOrderByStartDateDescIdDesc(any());
        verify(bondHoldingRepository, never()).findByPortfolioIdOrderByEntryDateDescIdDesc(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenHistoryPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(PORTFOLIO_ID, USER_SUB, "1Y"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
        verify(depositHoldingRepository, never()).findByPortfolioIdOrderByStartDateDescIdDesc(any());
        verify(bondHoldingRepository, never()).findByPortfolioIdOrderByEntryDateDescIdDesc(any());
    }

    // ---- summary totals / allocation ---------------------------------------------------------------------

    @Test
    void shouldAggregateTotalsAndAllocation_overActiveClosedDepositAndBond() {
        ownsPortfolio();
        LocalDate start = LocalDate.now().minusMonths(6);
        DepositHolding active = tryDeposit(1L, new BigDecimal("100000"), start);
        DepositHolding closed = closedTryDeposit(2L, new BigDecimal("50000"), start, new BigDecimal("55000"));
        BondHolding heldBond = bond(10L, new BigDecimal("10"), new BigDecimal("98"), start);
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(active, closed));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(heldBond));
        // Active deposit live value 110000 TRY; closed deposit frozen realized value 55000 TRY.
        when(depositAccrualService.realizedOrAccruedValue(eq(active), any())).thenReturn(new BigDecimal("110000"));
        when(depositAccrualService.realizedOrAccruedValue(eq(closed), any())).thenReturn(new BigDecimal("55000"));
        // Bond clean price 100 per 100 nominal -> 1000 TRY value; entry 98 -> 980 TRY cost.
        when(bondValuationService.currentValueTry(eq(heldBond), any())).thenReturn(new BigDecimal("1000"));

        FixedIncomeSummaryResponse response = service.summary(PORTFOLIO_ID, USER_SUB);

        // depositValue = 110000 + 55000 = 165000 ; bondValue = 1000 ; total = 166000
        assertThat(response.depositValueTry()).isEqualByComparingTo("165000");
        assertThat(response.bondValueTry()).isEqualByComparingTo("1000");
        assertThat(response.totalValueTry()).isEqualByComparingTo("166000");
        // cost = depositPrincipal(100000 + 50000) + bondEntry(980) = 150980
        assertThat(response.totalCostTry()).isEqualByComparingTo("150980");
        assertThat(response.totalPnlTry()).isEqualByComparingTo("15020");
        assertThat(response.depositCount()).isEqualTo(2);
        assertThat(response.bondCount()).isEqualTo(1);
        assertThat(response.asOf()).isEqualTo(LocalDate.now());
    }

    @Test
    void shouldConvertForeignDepositPrincipalAndValueToTry_inSummary() {
        ownsPortfolio();
        LocalDate start = LocalDate.now().minusMonths(3);
        DepositHolding usdDeposit = DepositHolding.builder()
                .currency("USD")
                .principal(new BigDecimal("1000"))
                .annualRate(new BigDecimal("10"))
                .startDate(start)
                .maturityDate(start.plusYears(1))
                .build();
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(usdDeposit));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(depositAccrualService.realizedOrAccruedValue(eq(usdDeposit), any())).thenReturn(new BigDecimal("1050"));
        // principal 1000 USD @ entry -> 30000 TRY ; accrued 1050 USD @ today -> 33000 TRY.
        when(currencyConverter.convertAtDate(eq(new BigDecimal("1000")), any(), any(), eq(start)))
                .thenReturn(new BigDecimal("30000"));
        when(currencyConverter.convertAtDate(eq(new BigDecimal("1050")), any(), any(), eq(LocalDate.now())))
                .thenReturn(new BigDecimal("33000"));

        FixedIncomeSummaryResponse response = service.summary(PORTFOLIO_ID, USER_SUB);

        assertThat(response.totalValueTry()).isEqualByComparingTo("33000");
        assertThat(response.totalCostTry()).isEqualByComparingTo("30000");
        assertThat(response.totalPnlTry()).isEqualByComparingTo("3000");
        assertThat(response.pnlPercent()).isEqualByComparingTo("10");
    }

    @Test
    void shouldDegradeGracefully_whenDepositCostFxUnavailable_inSummary() {
        // Arrange: an active USD deposit whose START-date FX rate is missing (cost cannot convert) while today's
        // value rate IS available. Summary must omit just that cost leg, NOT 500 the whole headline — matching the
        // per-holding FX-gap tolerance the history series already has.
        ownsPortfolio();
        LocalDate start = LocalDate.now().minusMonths(3);
        DepositHolding usd = usdDeposit(1L, new BigDecimal("1000"), start);
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(usd));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(depositAccrualService.realizedOrAccruedValue(eq(usd), any())).thenReturn(new BigDecimal("1050"));
        // Cost @ start throws (no rate); value @ today converts to 33000 TRY.
        when(currencyConverter.convertAtDate(eq(new BigDecimal("1000")), eq(Currency.USD), eq(Currency.TRY), eq(start)))
                .thenThrow(new FxRateUnavailableException(Currency.USD, Currency.TRY, start));
        when(currencyConverter.convertAtDate(eq(new BigDecimal("1050")), eq(Currency.USD), eq(Currency.TRY),
                eq(LocalDate.now()))).thenReturn(new BigDecimal("33000"));

        FixedIncomeSummaryResponse response = service.summary(PORTFOLIO_ID, USER_SUB);

        // The headline still returns: value converted (33000), the unconvertible cost leg omitted (0).
        assertThat(response.totalValueTry()).isEqualByComparingTo("33000");
        assertThat(response.totalCostTry()).isEqualByComparingTo("0");
        assertThat(response.totalPnlTry()).isEqualByComparingTo("33000");
        // Cost is zero, so pnlPercent is undefined (null), never a divide-by-zero.
        assertThat(response.pnlPercent()).isNull();
    }

    @Test
    void shouldReturnZerosAndNullPnlPercent_whenPortfolioEmpty() {
        ownsPortfolio();
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());

        FixedIncomeSummaryResponse response = service.summary(PORTFOLIO_ID, USER_SUB);

        assertThat(response.totalValueTry()).isEqualByComparingTo("0");
        assertThat(response.totalCostTry()).isEqualByComparingTo("0");
        assertThat(response.totalPnlTry()).isEqualByComparingTo("0");
        assertThat(response.pnlPercent()).isNull();
        assertThat(response.depositCount()).isZero();
        assertThat(response.bondCount()).isZero();
    }

    // ---- history zeroing before start/entry --------------------------------------------------------------

    @Test
    void shouldZeroLaterHolding_beforeItsEntry_andClampBoundedWindowToEarliest() {
        ownsPortfolio();
        LocalDate today = LocalDate.now();
        // Deposit comes online 5 days ago (the earliest holding); the bond only 2 days ago. A 1M window must
        // CLAMP to the earliest holding (no flat month of pre-entry zeros), and the later bond stays zero on the
        // days before its own entry.
        LocalDate depositOnline = today.minusDays(5);
        LocalDate bondOnline = today.minusDays(2);
        DepositHolding deposit = tryDeposit(1L, new BigDecimal("100000"), depositOnline);
        BondHolding heldBond = bond(10L, new BigDecimal("10"), new BigDecimal("98"), bondOnline);
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(deposit));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(heldBond));
        // The history series values an active deposit per-date via accruedValue (not realizedOrAccruedValue).
        when(depositAccrualService.accruedValue(eq(deposit), any())).thenReturn(new BigDecimal("100000"));
        // The batched history path resolves the bond clean price in-memory from the once-loaded rate series
        // (NOT a per-date BondValuationService call): a clean price of 100 -> 100 × 1000 / 100 = 1000 TRY.
        when(bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(heldBond.getBondIsin()))
                .thenReturn(List.of(rateRow(bondOnline, new BigDecimal("100"))));

        List<FixedIncomeHistoryPoint> points = service.history(PORTFOLIO_ID, USER_SUB, "1M");

        // The window starts at the earliest holding (deposit online), already valued — NOT a pre-entry zero run.
        FixedIncomeHistoryPoint first = points.get(0);
        assertThat(first.date()).isEqualTo(depositOnline);
        assertThat(first.depositValueTry()).isEqualByComparingTo("100000");
        // The bond is still zero on the opening day — it only comes online three days later.
        assertThat(first.bondValueTry()).isEqualByComparingTo("0");
        assertThat(first.totalValueTry()).isEqualByComparingTo("100000");
        // Only the deposit is online on the opening day, so cost is its principal alone (100000 TRY).
        assertThat(first.totalCostTry()).isEqualByComparingTo("100000");

        FixedIncomeHistoryPoint last = points.get(points.size() - 1);
        assertThat(last.date()).isEqualTo(today);
        assertThat(last.depositValueTry()).isEqualByComparingTo("100000");
        assertThat(last.bondValueTry()).isEqualByComparingTo("1000");
        assertThat(last.totalValueTry()).isEqualByComparingTo("101000");
        // By today both are online: deposit principal 100000 + bond entry value (98 × 1000 / 100 = 980) = 100980.
        assertThat(last.totalCostTry()).isEqualByComparingTo("100980");
        // The per-date loop must NOT issue a per-(bond,date) valuation call — the series is loaded once.
        verify(bondValuationService, never()).currentValueTry(any(), any());
    }

    @Test
    void shouldClampAllPeriodToEarliestHolding_inHistory() {
        ownsPortfolio();
        LocalDate today = LocalDate.now();
        LocalDate earliest = today.minusDays(2);
        DepositHolding deposit = tryDeposit(1L, new BigDecimal("100000"), earliest);
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(deposit));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(depositAccrualService.accruedValue(eq(deposit), any())).thenReturn(new BigDecimal("100000"));

        List<FixedIncomeHistoryPoint> points = service.history(PORTFOLIO_ID, USER_SUB, "ALL");

        // ALL clamps to the earliest holding date, not the Unix epoch: 3 points (earliest..today inclusive).
        assertThat(points).hasSize(3);
        assertThat(points.get(0).date()).isEqualTo(earliest);
        assertThat(points.get(points.size() - 1).date()).isEqualTo(today);
    }

    @Test
    void shouldReturnSingleTodayPoint_whenHistoryPortfolioEmpty() {
        ownsPortfolio();
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());

        List<FixedIncomeHistoryPoint> points = service.history(PORTFOLIO_ID, USER_SUB, "ALL");

        assertThat(points).hasSize(1);
        assertThat(points.get(0).date()).isEqualTo(LocalDate.now());
        assertThat(points.get(0).totalValueTry()).isEqualByComparingTo("0");
    }

    // ---- closed bond is valued at its frozen exit price, not today's live price (bug 1) ------------------

    @Test
    void shouldValueClosedBondAtFrozenExitPrice_notLivePrice_inSummary() {
        // Arrange: a SOLD bond (exit 90, entry 100) whose live clean price today is a different 110.
        ownsPortfolio();
        LocalDate start = LocalDate.now().minusMonths(6);
        BondHolding sold = closedBond(10L, new BigDecimal("10"), new BigDecimal("100"),
                start, LocalDate.now().minusDays(7), new BigDecimal("90"));
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(sold));
        // The stubbed live valuation (1100) must NEVER reach the headline for a closed bond (class is LENIENT).
        when(bondValuationService.currentValueTry(eq(sold), any())).thenReturn(new BigDecimal("1100"));

        FixedIncomeSummaryResponse response = service.summary(PORTFOLIO_ID, USER_SUB);

        // value = exitPrice 90 × 1000 / 100 = 900 ; cost = entry 100 × 1000 / 100 = 1000 ; pnl = -100 (frozen).
        assertThat(response.bondValueTry()).isEqualByComparingTo("900");
        assertThat(response.totalValueTry()).isEqualByComparingTo("900");
        assertThat(response.totalCostTry()).isEqualByComparingTo("1000");
        assertThat(response.totalPnlTry()).isEqualByComparingTo("-100");
        verify(bondValuationService, never()).currentValueTry(eq(sold), any());
    }

    // ---- history holds a sold bond's FROZEN exit proceeds from after its exit date, reconciling with the
    //      headline summary (and matching the closed-deposit treatment) -------------------------------------

    @Test
    void shouldHoldSoldBondFrozenProceedsAfterExit_inHistory() {
        // Arrange: a bond entered at the window open, sold 1 day ago. No rate history -> clean price falls back
        // to the entry price 100 while open. Exit price 95 -> frozen proceeds 95 × 1000 / 100 = 950.
        ownsPortfolio();
        LocalDate today = LocalDate.now();
        LocalDate entry = today.minusDays(3);
        LocalDate exit = today.minusDays(1);
        BondHolding sold = closedBond(10L, new BigDecimal("10"), new BigDecimal("100"),
                entry, exit, new BigDecimal("95"));
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(sold));
        // Summary values the closed bond at its frozen exit proceeds (950) — the headline the chart must match.
        when(bondValuationService.currentValueTry(eq(sold), any())).thenReturn(new BigDecimal("1100"));

        List<FixedIncomeHistoryPoint> points = service.history(PORTFOLIO_ID, USER_SUB, "1M");

        // BEFORE exit: live value (entry-price fallback) contributes 1000. ON and AFTER exit: the bond was sold at
        // exitPrice that day, so the value is the FROZEN proceeds 950 — making a bond sold today reconcile with the
        // headline summary (which always values a closed bond at its exit proceeds).
        FixedIncomeHistoryPoint beforeExit = points.stream().filter(p -> p.date().equals(exit.minusDays(1)))
                .findFirst().orElseThrow();
        assertThat(beforeExit.bondValueTry()).isEqualByComparingTo("1000");
        FixedIncomeHistoryPoint onExit = points.stream().filter(p -> p.date().equals(exit))
                .findFirst().orElseThrow();
        assertThat(onExit.bondValueTry()).isEqualByComparingTo("950");
        FixedIncomeHistoryPoint todayPoint = points.get(points.size() - 1);
        assertThat(todayPoint.bondValueTry()).isEqualByComparingTo("950");

        // The chart's today endpoint reconciles with the headline summary's total for the same sold bond.
        FixedIncomeSummaryResponse summary = service.summary(PORTFOLIO_ID, USER_SUB);
        assertThat(todayPoint.totalValueTry()).isEqualByComparingTo(summary.totalValueTry());
    }

    // ---- THREE-WAY reconciliation: summaryCard == last history point == Σ grid currentValueTry ----------

    @Test
    void shouldReconcileSummaryHistoryAndGrid_overActiveClosedDepositAndOpenSoldBond() {
        // Arrange a single TRY book that exercises EVERY closed-holding path at once: an active deposit, a
        // closed deposit, an open bond and a sold bond. The same mocks back all three surfaces, so the headline
        // total, the chart's today endpoint, and the sum of the grid rows must all be the identical TRY figure.
        ownsPortfolio();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(5);
        LocalDate depositClosed = today.minusDays(2);
        LocalDate bondExit = today.minusDays(2);

        DepositHolding active = tryDeposit(1L, new BigDecimal("100000"), start);
        DepositHolding closedDeposit = tryDeposit(2L, new BigDecimal("50000"), start);
        closedDeposit.close(depositClosed, new BigDecimal("52000"));
        BondHolding openBond = bond(10L, new BigDecimal("10"), new BigDecimal("98"), start);
        BondHolding soldBond = closedBond(11L, new BigDecimal("20"), new BigDecimal("100"),
                start, bondExit, new BigDecimal("103"));
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID))
                .thenReturn(List.of(active, closedDeposit));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(openBond, soldBond));

        // Active deposit: realizedOrAccrued (summary + grid) AND accrued (history) at today must agree on 108000.
        when(depositAccrualService.realizedOrAccruedValue(eq(active), any())).thenReturn(new BigDecimal("108000"));
        when(depositAccrualService.accruedValue(eq(active), any())).thenReturn(new BigDecimal("108000"));
        // Closed deposit: realizedOrAccrued returns the frozen closedValueTry (52000); history freezes the same.
        when(depositAccrualService.realizedOrAccruedValue(eq(closedDeposit), any()))
                .thenReturn(new BigDecimal("52000"));
        // Open bond: clean price 105 -> 105 × 1000 / 100 = 1050 TRY, consistent across all three surfaces. Summary
        // reads it via currentValueTry, the grid via cleanPriceTry, the chart via the once-loaded rate series.
        when(bondValuationService.currentValueTry(eq(openBond), any())).thenReturn(new BigDecimal("1050"));
        when(bondValuationService.cleanPriceTry(eq(openBond), any())).thenReturn(new BigDecimal("105"));
        when(bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(openBond.getBondIsin()))
                .thenReturn(List.of(rateRow(start, new BigDecimal("105"))));
        when(bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(soldBond.getBondIsin()))
                .thenReturn(List.of(rateRow(start, new BigDecimal("105"))));
        when(bondRepository.findById(any())).thenReturn(Optional.empty());

        // Act: value the same book through all three surfaces.
        FixedIncomeSummaryResponse summary = service.summary(PORTFOLIO_ID, USER_SUB);
        List<FixedIncomeHistoryPoint> points = service.history(PORTFOLIO_ID, USER_SUB, "1M");
        FixedIncomeHistoryPoint todayPoint = points.get(points.size() - 1);
        List<DepositHoldingResponse> depositRows = depositGridService.list(PORTFOLIO_ID, USER_SUB);
        List<BondHoldingResponse> bondRows = bondGridService.list(PORTFOLIO_ID, USER_SUB);
        BigDecimal gridTotal = depositRows.stream().map(DepositHoldingResponse::currentValueTry)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(bondRows.stream().map(BondHoldingResponse::currentValueTry)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        // Assert the goal invariant: all three surfaces agree on the SAME total TRY value.
        // active 108000 + closed 52000 + openBond 1050 + soldBond (103 × 2000 / 100 = 2060) = 163110.
        assertThat(summary.totalValueTry()).isEqualByComparingTo("163110");
        assertThat(todayPoint.totalValueTry()).isEqualByComparingTo(summary.totalValueTry());
        assertThat(gridTotal).isEqualByComparingTo(summary.totalValueTry());
    }

    // ---- closed deposit accrues day-by-day pre-close, freezes after (bug 3) -----------------------------

    @Test
    void shouldAccrueClosedDepositPerDatePreClose_andFreezeAfter_inHistory() {
        // Arrange: a TRY deposit started 3 days ago, closed 1 day ago at a frozen realized 130000.
        ownsPortfolio();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(3);
        LocalDate closed = today.minusDays(1);
        DepositHolding deposit = closedTryDeposit(1L, new BigDecimal("100000"), start, new BigDecimal("130000"));
        // Re-close on the precise mid-window date for this scenario.
        deposit.reopen();
        deposit.close(closed, new BigDecimal("130000"));
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(deposit));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        // Pre-close days read the live accrual curve (NOT a flat 130000): rising 110000 -> 120000.
        // Declare the catch-all FIRST so the date-specific stubs below win (last-matching-stub precedence).
        when(depositAccrualService.accruedValue(eq(deposit), any())).thenReturn(new BigDecimal("115000"));
        when(depositAccrualService.accruedValue(eq(deposit), eq(start))).thenReturn(new BigDecimal("110000"));
        when(depositAccrualService.accruedValue(eq(deposit), eq(closed))).thenReturn(new BigDecimal("120000"));

        List<FixedIncomeHistoryPoint> points = service.history(PORTFOLIO_ID, USER_SUB, "1M");

        FixedIncomeHistoryPoint startPoint = points.stream().filter(p -> p.date().equals(start))
                .findFirst().orElseThrow();
        // Pre-close point is the accrual value, not the flat close value.
        assertThat(startPoint.depositValueTry()).isEqualByComparingTo("110000");
        FixedIncomeHistoryPoint afterClose = points.get(points.size() - 1);
        // Post-close point holds the frozen realized value.
        assertThat(afterClose.depositValueTry()).isEqualByComparingTo("130000");
    }

    // ---- a single missing FX date degrades one point, not the whole series (bug 4 / 6) -----------------

    @Test
    void shouldForwardFillDepositValue_whenOneFxDateUnavailable_inHistory() {
        // Arrange: an active USD deposit; the FX converter throws on exactly the window's opening date.
        ownsPortfolio();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(2);
        DepositHolding usd = usdDeposit(1L, new BigDecimal("1000"), start);
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(usd));
        when(bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of());
        when(depositAccrualService.accruedValue(eq(usd), any())).thenReturn(new BigDecimal("1000"));
        // No rate on the first day -> throws; later days convert at 30 TRY/USD -> 30000 TRY.
        when(currencyConverter.convertAtDate(any(), eq(Currency.USD), eq(Currency.TRY), eq(start)))
                .thenThrow(new FxRateUnavailableException(Currency.USD, Currency.TRY, start));
        when(currencyConverter.convertAtDate(any(), eq(Currency.USD), eq(Currency.TRY),
                org.mockito.ArgumentMatchers.argThat(d -> d != null && d.isAfter(start))))
                .thenReturn(new BigDecimal("30000"));

        List<FixedIncomeHistoryPoint> points = service.history(PORTFOLIO_ID, USER_SUB, "ALL");

        // The series still returns in full (no 422/exception); the unconvertible first day forward-fills to 0
        // (no prior value yet), and the next days reflect the converted value.
        assertThat(points).hasSize(3);
        assertThat(points.get(0).depositValueTry()).isEqualByComparingTo("0");
        assertThat(points.get(points.size() - 1).depositValueTry()).isEqualByComparingTo("30000");
    }
}
