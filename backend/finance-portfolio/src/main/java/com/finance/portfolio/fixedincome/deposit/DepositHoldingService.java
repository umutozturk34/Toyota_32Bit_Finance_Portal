package com.finance.portfolio.fixedincome.deposit;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Currency;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.FxRateUnavailableException;
import com.finance.portfolio.dto.request.DepositHoldingRequest;
import com.finance.portfolio.dto.response.DepositHoldingResponse;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioType;
import com.finance.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Write/read side for hypothetical DEPOSIT (mevduat) holdings. Ownership is never trusted from a
 * {@code holdingId} alone: every method first loads the portfolio via
 * {@link PortfolioRepository#findByIdAndUserSub} (404 if the caller doesn't own it), and any existing-holding
 * path then asserts the holding actually belongs to that portfolio (422 {@code notInPortfolio}) before
 * mutating it. Valuation is delegated to {@link DepositAccrualService} (accrues in the holding's own
 * currency) and FX-converted to TRY here only for the response/realized figures.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DepositHoldingService {

    private final PortfolioRepository portfolioRepository;
    private final DepositHoldingRepository depositHoldingRepository;
    private final DepositAccrualService depositAccrualService;
    private final CurrencyConverter currencyConverter;

    /** Lists a portfolio's deposits as valued response rows; 404 if the portfolio isn't owned by the user. */
    @Transactional(readOnly = true)
    public List<DepositHoldingResponse> list(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        LocalDate today = LocalDate.now();
        return depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(portfolioId).stream()
                .map(h -> toResponse(h, today))
                .toList();
    }

    /** Validates and persists a new deposit under an owned portfolio. */
    @Transactional
    public DepositHoldingResponse add(Long portfolioId, String userSub, DepositHoldingRequest request) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userSub);
        // Deposits are fixed-income and belong only in a FIXED portfolio. Gated AFTER the ownership load so an
        // unowned portfolio still 404s ahead of this type check (no existence leak).
        portfolio.requireType(PortfolioType.FIXED);
        String currency = request.currencyOrDefault();
        DepositValidator.validate(currency, request);

        DepositHolding holding = DepositHolding.builder()
                .portfolio(portfolio)
                .currency(currency)
                .principal(scaled(request.principal()))
                .annualRate(request.annualRate())
                .indicatorCode(request.indicatorCode())
                .withholdingRate(request.withholdingRate())
                .startDate(request.startDate())
                .maturityDate(request.maturityDate())
                .build();
        DepositHolding saved = depositHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    /** Edits an owned deposit's terms in place after re-validating the resulting state. */
    @Transactional
    public DepositHoldingResponse update(Long portfolioId, Long holdingId, String userSub,
                                         DepositHoldingRequest request) {
        DepositHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        // A closed deposit has a closedValueTry frozen at its OLD terms; editing principal/rate/currency/dates here
        // would leave that realized value stale and inconsistent with the holding. Editing is only valid while
        // active — the caller must reopen() first (which clears the frozen value) before changing terms.
        if (!holding.isActive()) {
            throw new BusinessException("error.portfolio.deposit.alreadyClosed");
        }
        String currency = request.currencyOrDefault();
        DepositValidator.validate(currency, request);
        holding.update(scaled(request.principal()), request.annualRate(), request.startDate(),
                request.maturityDate(), currency, request.indicatorCode(), request.withholdingRate());
        DepositHolding saved = depositHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    /**
     * Closes an owned deposit at {@code when}, freezing the accrued value (converted to TRY) as its realized
     * value. {@code when} defaults to today when null; closing an already-closed deposit is rejected.
     */
    @Transactional
    public DepositHoldingResponse close(Long portfolioId, Long holdingId, String userSub, LocalDate when) {
        DepositHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        if (!holding.isActive()) {
            throw new BusinessException("error.portfolio.deposit.alreadyClosed");
        }
        LocalDate closeDate = when != null ? when : LocalDate.now();
        // Mirror the bond sell() bounds: a future close date would freeze a value that has not accrued (and a
        // foreign deposit would even fail FX conversion at that date), and a close before the deposit started
        // is nonsensical (it floors to bare principal but persists an invalid closedDate < startDate).
        if (closeDate.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.deposit.closeDateInFuture");
        }
        if (closeDate.isBefore(holding.getStartDate())) {
            throw new BusinessException("error.portfolio.deposit.closeBeforeStart");
        }
        BigDecimal accrued = depositAccrualService.accruedValue(holding, closeDate);
        holding.close(closeDate, toTry(accrued, holding.getCurrency(), closeDate));
        DepositHolding saved = depositHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    /** Re-opens an owned closed deposit, clearing its frozen realized value; rejects an already-active one. */
    @Transactional
    public DepositHoldingResponse reopen(Long portfolioId, Long holdingId, String userSub) {
        DepositHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        if (holding.isActive()) {
            throw new BusinessException("error.portfolio.deposit.notClosed");
        }
        holding.reopen();
        DepositHolding saved = depositHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    /** Deletes an owned deposit. */
    @Transactional
    public void delete(Long portfolioId, Long holdingId, String userSub) {
        DepositHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        depositHoldingRepository.delete(holding);
    }

    private Portfolio requireOwnedPortfolio(Long portfolioId, String userSub) {
        return portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
    }

    /**
     * Loads a holding only after proving the portfolio is owned by the user, then asserts the holding belongs
     * to that portfolio — so a valid holdingId from another (even owned) portfolio can never be mutated here.
     */
    private DepositHolding loadOwnedHolding(Long portfolioId, Long holdingId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        DepositHolding holding = depositHoldingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.position.notFound", holdingId));
        if (!holding.getPortfolioId().equals(portfolioId)) {
            throw new BusinessException("error.portfolio.position.notInPortfolio", portfolioId);
        }
        return holding;
    }

    /**
     * Maps a holding to its response, valuing it in TRY: the realized-or-accrued value (in the holding's
     * currency) is FX-converted to today's TRY, P&amp;L is taken against the TRY-converted principal.
     */
    private DepositHoldingResponse toResponse(DepositHolding h, LocalDate asOf) {
        BigDecimal valueNative = depositAccrualService.realizedOrAccruedValue(h, asOf);
        // A closed deposit already stores its realized value in TRY; only a live accrued value is still in the
        // holding's currency and needs converting.
        BigDecimal currentValueTry = h.isActive()
                ? toTry(valueNative, h.getCurrency(), asOf)
                : valueNative;
        // Cost basis = principal converted at the deposit's START date (its entry cost for this hypothetical-lot
        // product), NOT asOf — otherwise a foreign deposit's FX move cancels between value and cost so the row's
        // PnL can never reflect an FX-driven gain/loss, and it would diverge from the FX-at-start headline summary.
        BigDecimal principalTry = toTry(h.getPrincipal(), h.getCurrency(), h.getStartDate());

        BigDecimal pnlTry = currentValueTry == null || principalTry == null ? null
                : currentValueTry.subtract(principalTry).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = pnlTry == null || principalTry == null
                || principalTry.compareTo(BigDecimal.ZERO) == 0 ? null
                : pnlTry.multiply(new BigDecimal("100"))
                .divide(principalTry, MoneyScale.PRICE, RoundingMode.HALF_UP);

        // Interest breakdown (gross / stopaj / net), TRY-converted at the value date so the UI can show how much
        // interest accrued, how much stopaj is withheld, and the net the holder receives. Computed at asOf for an
        // active deposit, at the close date for a closed one (its realized figures). Null-tolerant: a null breakdown
        // (e.g. a stubbed accrual in unit tests) leaves the legs null rather than failing the row.
        LocalDate valueDate = h.isActive() ? asOf : h.getClosedDate();
        DepositAccrualService.DepositAccrual accrual = valueDate == null ? null
                : depositAccrualService.breakdown(h, valueDate);
        BigDecimal grossInterestTry = accrual == null ? null : toTry(accrual.grossInterest(), h.getCurrency(), valueDate);
        BigDecimal withholdingTaxTry = accrual == null ? null : toTry(accrual.withholdingTax(), h.getCurrency(), valueDate);
        BigDecimal netInterestTry = accrual == null ? null : toTry(accrual.netInterest(), h.getCurrency(), valueDate);

        return new DepositHoldingResponse(h.getId(), h.getCurrency(), h.getPrincipal(), h.getAnnualRate(),
                h.getIndicatorCode(), h.getStartDate(), h.getMaturityDate(), h.getClosedDate(), h.isActive(),
                currentValueTry, pnlTry, pnlPercent,
                grossInterestTry, withholdingTaxTry, netInterestTry, depositAccrualService.effectiveWithholdingRate(h));
    }

    /**
     * Converts a holding-currency amount to TRY at the as-of date's FX rate; a TRY amount passes through.
     * Degrades a missing FX observation to {@code null} (not an exception): a foreign deposit whose value
     * (today) or cost (start date) leg has an FX gap — a stale forex feed, or a start date before the earliest
     * seeded candle — would otherwise let {@link CurrencyConverter#convertAtDate}'s
     * {@link FxRateUnavailableException} (422) bubble out of {@code toResponse} and fail the ENTIRE deposit grid
     * and every mutation response. Mirroring {@code FixedIncomeSummaryService}/{@code FixedIncomeHistoryService},
     * we instead null just the affected leg so the row's downstream null checks blank that one figure while the
     * rest of the grid stays alive.
     */
    private BigDecimal toTry(BigDecimal amount, String currencyCode, LocalDate asOf) {
        if (amount == null) {
            return null;
        }
        Currency from = Currency.fromCode(currencyCode);
        if (from == null || from == Currency.TRY) {
            return amount;
        }
        try {
            return currencyConverter.convertAtDate(amount, from, Currency.TRY, asOf);
        } catch (FxRateUnavailableException ex) {
            log.debug("No FX rate for {} -> TRY at {} — degrading deposit leg to null", from, asOf);
            return null;
        }
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }
}
