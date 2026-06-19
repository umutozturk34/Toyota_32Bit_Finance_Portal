package com.finance.portfolio.fixedincome.deposit;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Currency;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.FxRateUnavailableException;
import com.finance.portfolio.dto.request.DepositHoldingRequest;
import com.finance.portfolio.dto.response.DepositHoldingResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositHoldingServiceTest {

    private static final String USER_SUB = "user-1";
    private static final Long PORTFOLIO_ID = 7L;
    private static final Long OTHER_PORTFOLIO_ID = 999L;
    private static final Long HOLDING_ID = 33L;

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private DepositHoldingRepository depositHoldingRepository;
    @Mock private DepositAccrualService depositAccrualService;
    @Mock private CurrencyConverter currencyConverter;

    private DepositHoldingService service;

    @BeforeEach
    void setUp() {
        service = new DepositHoldingService(portfolioRepository, depositHoldingRepository,
                depositAccrualService, currencyConverter);
    }

    private Portfolio ownedPortfolio() {
        return Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB)
                .type(PortfolioType.FIXED).build();
    }

    private DepositHoldingRequest validRequest() {
        return new DepositHoldingRequest("TRY", new BigDecimal("100000"), new BigDecimal("45"),
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
    }

    private DepositHolding holdingIn(Long portfolioId) {
        return DepositHolding.builder()
                .id(HOLDING_ID)
                .portfolioId(portfolioId)
                .currency("TRY")
                .principal(new BigDecimal("100000.00000000"))
                .annualRate(new BigDecimal("45.0000"))
                .startDate(LocalDate.now().minusYears(1))
                .maturityDate(LocalDate.now().plusYears(1))
                .build();
    }

    // ---- add ----------------------------------------------------------------------------------------------

    @Test
    void shouldPersistDeposit_whenPortfolioOwnedByUser() {
        Portfolio portfolio = ownedPortfolio();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(depositHoldingRepository.save(any(DepositHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(depositAccrualService.realizedOrAccruedValue(any(), any()))
                .thenReturn(new BigDecimal("110000"));

        service.add(PORTFOLIO_ID, USER_SUB, validRequest());

        ArgumentCaptor<DepositHolding> captor = ArgumentCaptor.forClass(DepositHolding.class);
        verify(depositHoldingRepository).save(captor.capture());
        DepositHolding saved = captor.getValue();
        assertThat(saved.getPortfolio()).isSameAs(portfolio);
        assertThat(saved.getCurrency()).isEqualTo("TRY");
        assertThat(saved.getPrincipal()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(saved.getAnnualRate()).isEqualByComparingTo(new BigDecimal("45"));
    }

    @Test
    void shouldDefaultCurrencyToTry_whenRequestCurrencyIsNull() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.save(any(DepositHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        DepositHoldingRequest req = new DepositHoldingRequest(null, new BigDecimal("5000"),
                new BigDecimal("30"), LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(11));

        service.add(PORTFOLIO_ID, USER_SUB, req);

        ArgumentCaptor<DepositHolding> captor = ArgumentCaptor.forClass(DepositHolding.class);
        verify(depositHoldingRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrency()).isEqualTo("TRY");
    }

    @Test
    void shouldConvertForeignCurrencyValueToTry_whenBuildingResponse() {
        LocalDate start = LocalDate.now().minusMonths(6);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.save(any(DepositHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(depositAccrualService.realizedOrAccruedValue(any(), any())).thenReturn(new BigDecimal("1100"));
        // The cost leg converts the principal at the deposit's START date (FX 30 then), the value leg at TODAY
        // (FX 35 now) — so the row's PnL captures BOTH the accrual AND the FX move, matching the headline summary.
        // accrued 1100 USD @ today -> 38500 TRY ; principal 1000 USD @ start -> 30000 TRY.
        when(currencyConverter.convertAtDate(eq(new BigDecimal("1100")), eq(Currency.USD), eq(Currency.TRY), any()))
                .thenReturn(new BigDecimal("38500"));
        when(currencyConverter.convertAtDate(eq(new BigDecimal("1000.00000000")), eq(Currency.USD), eq(Currency.TRY), eq(start)))
                .thenReturn(new BigDecimal("30000"));
        DepositHoldingRequest req = new DepositHoldingRequest("USD", new BigDecimal("1000"),
                new BigDecimal("10"), start, LocalDate.now().plusMonths(6));

        DepositHoldingResponse response = service.add(PORTFOLIO_ID, USER_SUB, req);

        // value 38500, cost 30000 (FX-at-start) -> pnl 8500 (= accrual + FX), pnl% = 8500/30000*100.
        assertThat(response.currentValueTry()).isEqualByComparingTo(new BigDecimal("38500"));
        assertThat(response.pnlTry()).isEqualByComparingTo(new BigDecimal("8500"));
        assertThat(response.pnlPercent()).isEqualByComparingTo(new BigDecimal("28.33333333"));
    }

    @Test
    void shouldDegradeRowFiguresToNull_whenFxRateUnavailable_ratherThanFailingTheWholeGrid() {
        // A foreign deposit whose FX leg has no rate (stale forex feed, or a start date before the earliest
        // seeded candle) must NOT 422 the entire deposit list/mutation response: just this row's FX-dependent
        // figures degrade to null, mirroring FixedIncomeSummaryService/FixedIncomeHistoryService.
        LocalDate start = LocalDate.now().minusMonths(6);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.save(any(DepositHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(depositAccrualService.realizedOrAccruedValue(any(), any())).thenReturn(new BigDecimal("1100"));
        when(currencyConverter.convertAtDate(any(), eq(Currency.USD), eq(Currency.TRY), any()))
                .thenThrow(new FxRateUnavailableException(Currency.USD, Currency.TRY, start));
        DepositHoldingRequest req = new DepositHoldingRequest("USD", new BigDecimal("1000"),
                new BigDecimal("10"), start, LocalDate.now().plusMonths(6));

        DepositHoldingResponse response = service.add(PORTFOLIO_ID, USER_SUB, req);

        assertThat(response).isNotNull();
        assertThat(response.currentValueTry()).isNull();
        assertThat(response.pnlTry()).isNull();
        assertThat(response.pnlPercent()).isNull();
    }

    // ---- cross-user ownership matrix (the non-negotiable) -------------------------------------------------

    @Test
    void shouldThrowResourceNotFound_whenListingPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(PORTFOLIO_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
        verify(depositHoldingRepository, never()).findByPortfolioIdOrderByStartDateDescIdDesc(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenAddingToPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, validRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectAdd_whenPortfolioIsSpotType() {
        Portfolio spot = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB)
                .type(PortfolioType.SPOT).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(spot));

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.notFixedType");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenUpdatingUnderPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, validRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(depositHoldingRepository, never()).findById(any());
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenClosingUnderPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(PORTFOLIO_ID, HOLDING_ID, USER_SUB, LocalDate.now()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(depositHoldingRepository, never()).findById(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenReopeningUnderPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(depositHoldingRepository, never()).findById(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenDeletingUnderPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(depositHoldingRepository, never()).delete(any());
    }

    // ---- foreign holding under an owned portfolio --------------------------------------------------------

    @Test
    void shouldThrowNotInPortfolio_whenHoldingBelongsToAnotherPortfolio() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holdingIn(OTHER_PORTFOLIO_ID)));

        assertThatThrownBy(() -> service.update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.position.notInPortfolio");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdate_whenDepositAlreadyClosed() {
        // Arrange: an owned but CLOSED deposit. Its closedValueTry is frozen at the old terms, so editing
        // principal/rate/dates would orphan that realized value — the caller must reopen() first.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHolding closed = holdingIn(PORTFOLIO_ID);
        closed.close(LocalDate.now().minusDays(1), new BigDecimal("130000"));
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(closed));

        // Act + Assert: rejected as alreadyClosed and nothing is persisted.
        assertThatThrownBy(() -> service.update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.alreadyClosed");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenHoldingMissingUnderOwnedPortfolio() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.position.notFound");
    }

    // ---- close / reopen transitions ----------------------------------------------------------------------

    @Test
    void shouldFreezeAccruedValueAsRealized_whenClosing() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHolding holding = holdingIn(PORTFOLIO_ID);
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding));
        when(depositHoldingRepository.save(any(DepositHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate closeDate = LocalDate.now();
        when(depositAccrualService.accruedValue(holding, closeDate)).thenReturn(new BigDecimal("142000"));
        // Once closed, the response reads realizedOrAccruedValue, which the real accrual returns as the frozen
        // closedValueTry; mirror that so the response reflects the value frozen by close().
        when(depositAccrualService.realizedOrAccruedValue(eq(holding), any())).thenReturn(new BigDecimal("142000"));

        DepositHoldingResponse response = service.close(PORTFOLIO_ID, HOLDING_ID, USER_SUB, closeDate);

        assertThat(holding.isActive()).isFalse();
        assertThat(holding.getClosedDate()).isEqualTo(closeDate);
        assertThat(holding.getClosedValueTry()).isEqualByComparingTo(new BigDecimal("142000"));
        assertThat(response.active()).isFalse();
        assertThat(response.currentValueTry()).isEqualByComparingTo(new BigDecimal("142000"));
    }

    @Test
    void shouldRejectClose_whenCloseDateInFuture() {
        // Arrange: an active deposit, close requested for a future date (direct-API bypass of the FE cap).
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holdingIn(PORTFOLIO_ID)));

        // Act + Assert
        assertThatThrownBy(() -> service.close(PORTFOLIO_ID, HOLDING_ID, USER_SUB, LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.closeDateInFuture");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectClose_whenCloseDateBeforeStart() {
        // Arrange: holding starts a year ago; a close date before that is nonsensical.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHolding holding = holdingIn(PORTFOLIO_ID);
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding));

        // Act + Assert
        assertThatThrownBy(() -> service.close(PORTFOLIO_ID, HOLDING_ID, USER_SUB,
                holding.getStartDate().minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.closeBeforeStart");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectClose_whenAlreadyClosed() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHolding holding = holdingIn(PORTFOLIO_ID);
        holding.close(LocalDate.now().minusDays(1), new BigDecimal("120000"));
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding));

        assertThatThrownBy(() -> service.close(PORTFOLIO_ID, HOLDING_ID, USER_SUB, LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.alreadyClosed");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldClearRealizedValue_whenReopening() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHolding holding = holdingIn(PORTFOLIO_ID);
        holding.close(LocalDate.now().minusDays(1), new BigDecimal("120000"));
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding));
        when(depositHoldingRepository.save(any(DepositHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(depositAccrualService.realizedOrAccruedValue(any(), any())).thenReturn(new BigDecimal("130000"));

        DepositHoldingResponse response = service.reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB);

        assertThat(holding.isActive()).isTrue();
        assertThat(holding.getClosedDate()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldRejectReopen_whenStillActive() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holdingIn(PORTFOLIO_ID)));

        assertThatThrownBy(() -> service.reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.notClosed");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldDeleteOwnedHolding_whenItBelongsToThePortfolio() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHolding holding = holdingIn(PORTFOLIO_ID);
        when(depositHoldingRepository.findById(HOLDING_ID)).thenReturn(Optional.of(holding));

        service.delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB);

        verify(depositHoldingRepository).delete(holding);
    }

    @Test
    void shouldListValuedHoldings_whenPortfolioOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHolding holding = holdingIn(PORTFOLIO_ID);
        when(depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(PORTFOLIO_ID)).thenReturn(List.of(holding));
        when(depositAccrualService.realizedOrAccruedValue(eq(holding), any())).thenReturn(new BigDecimal("145000"));

        List<DepositHoldingResponse> responses = service.list(PORTFOLIO_ID, USER_SUB);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).currentValueTry()).isEqualByComparingTo(new BigDecimal("145000"));
        assertThat(responses.get(0).pnlTry()).isEqualByComparingTo(new BigDecimal("45000"));
    }

    // ---- validation rejections (right key per bad input) -------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "GBP,     100000, 45,   error.portfolio.deposit.currencyUnsupported",
            "TRY,     0,      45,   error.portfolio.deposit.principalTooLow",
            "TRY,     100000, 1001, error.portfolio.deposit.rateOutOfRange",
            "TRY,     100000, -1,   error.portfolio.deposit.rateOutOfRange"
    })
    void shouldRejectAdd_whenFieldOutOfBounds(String currency, BigDecimal principal, BigDecimal rate, String key) {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHoldingRequest req = new DepositHoldingRequest(currency, principal, rate,
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(key);
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldRejectAdd_whenStartDateInFuture() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHoldingRequest req = new DepositHoldingRequest("TRY", new BigDecimal("100000"),
                new BigDecimal("45"), LocalDate.now().plusDays(1), LocalDate.now().plusYears(1));

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.startDateInFuture");
    }

    @Test
    void shouldRejectAdd_whenStartDateTooOld() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHoldingRequest req = new DepositHoldingRequest("TRY", new BigDecimal("100000"),
                new BigDecimal("45"), LocalDate.of(1999, 1, 1), LocalDate.of(2001, 1, 1));

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.startDateTooOld");
    }

    @Test
    void shouldRejectAdd_whenMaturityNotAfterStart() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        LocalDate sameDay = LocalDate.now().minusMonths(1);
        DepositHoldingRequest req = new DepositHoldingRequest("TRY", new BigDecimal("100000"),
                new BigDecimal("45"), sameDay, sameDay);

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.maturityBeforeStart");
    }

    @Test
    void shouldRejectAdd_whenMaturityTooFar() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        LocalDate start = LocalDate.now().minusYears(1);
        DepositHoldingRequest req = new DepositHoldingRequest("TRY", new BigDecimal("100000"),
                new BigDecimal("45"), start, start.plusYears(31));

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.maturityTooFar");
    }

    @Test
    void shouldRejectAdd_whenPrincipalTooHigh() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        DepositHoldingRequest req = new DepositHoldingRequest("TRY", new BigDecimal("99999999999999"),
                new BigDecimal("45"), LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.principalTooHigh");
    }

    @Test
    void shouldRejectAdd_whenProjectedMaturityValueOverflowsColumn() {
        // Arrange: every field is INDIVIDUALLY legal — principal 100bn (< 1e13 cap), rate 500% (== cap), a
        // 30-year term (== cap) — yet SIMPLE interest for 30y at 500% (×151) still pushes the maturity value
        // (~1.51e13) past the numeric(23,8) money column (~1e15 headroom, 1e13 guard). Pre-fix this persisted and
        // only blew up later on close()/persist as a DB numeric-overflow; now the validator rejects it up front.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        LocalDate start = LocalDate.now().minusYears(1);
        DepositHoldingRequest req = new DepositHoldingRequest("TRY", new BigDecimal("100000000000"),
                new BigDecimal("500"), start, start.plusYears(30));

        assertThatThrownBy(() -> service.add(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.deposit.projectedValueTooHigh");
        verify(depositHoldingRepository, never()).save(any());
    }

    @Test
    void shouldAcceptAdd_whenProjectedMaturityValueFitsColumn() {
        // A realistic deposit (1m principal, 45% for 1y) projects well under the cap and must still be accepted,
        // so the overflow guard does not over-reach and reject ordinary deposits.
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(ownedPortfolio()));
        when(depositHoldingRepository.save(any(DepositHolding.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(depositAccrualService.realizedOrAccruedValue(any(), any()))
                .thenReturn(new BigDecimal("1450000"));
        LocalDate start = LocalDate.now().minusMonths(1);
        DepositHoldingRequest req = new DepositHoldingRequest("TRY", new BigDecimal("1000000"),
                new BigDecimal("45"), start, start.plusYears(1));

        service.add(PORTFOLIO_ID, USER_SUB, req);

        verify(depositHoldingRepository).save(any(DepositHolding.class));
    }
}
