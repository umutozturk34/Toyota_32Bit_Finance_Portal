package com.finance.portfolio.fixedincome;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.core.service.FxRateUnavailableException;
import com.finance.market.bond.model.Bond;
import com.finance.portfolio.dto.response.FixedIncomeHistoryPoint;
import com.finance.portfolio.dto.response.FixedIncomeSummaryResponse;
import com.finance.portfolio.fixedincome.bond.BondCouponEventBuilder;
import com.finance.portfolio.fixedincome.bond.BondHolding;
import com.finance.portfolio.fixedincome.bond.BondHoldingRepository;
import com.finance.portfolio.fixedincome.bond.BondValuationService;
import com.finance.portfolio.fixedincome.deposit.DepositHolding;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingRepository;
import com.finance.portfolio.model.MoneyScale;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Read-side aggregate for the standalone "Mevduat &amp; Tahvil" (deposit + Türkiye Hazine bond) view: the
 * headline TRY totals + per-kind allocation. Deliberately SEPARATE from the spot/VIOP
 * {@code PortfolioSummaryService} — fixed-income holdings live in their own tables and are valued by their own
 * services, so folding them into the spot snapshot core would conflate two products. The value-over-time series
 * is delegated to {@link FixedIncomeHistoryService}; both share the valuation primitives in
 * {@link FixedIncomeValuationSupport}.
 *
 * <p>Ownership is non-negotiable: every public method first resolves the portfolio (404 if the caller doesn't
 * own it) BEFORE any holding is loaded, so a portfolio id alone can never leak another user's fixed-income book.
 *
 * <p>Currency: bonds are ALWAYS TRY. Deposits accrue in their own currency, so an active deposit's value is
 * FX-converted to TRY at the as-of date and its cost (principal) at its entry (start) date — reusing the SAME
 * converter the deposit response uses, so the headline reconciles with the grid.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class FixedIncomeSummaryService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DepositHoldingRepository depositHoldingRepository;
    private final BondHoldingRepository bondHoldingRepository;
    private final BondValuationService bondValuationService;
    private final BondCouponEventBuilder bondCouponEventBuilder;
    private final FixedIncomeValuationSupport support;
    private final FixedIncomeHistoryService historyService;

    /**
     * Headline TRY figures for the view as of today: total cost/value/PnL, the per-kind value split and
     * counts. Cost is the deposit principal (FX→TRY at entry) plus bond entry value; value is the
     * accrued/realized deposit value (FX→TRY at today for an active foreign deposit) plus bond clean-price
     * valuation. {@code pnlPercent} is null when cost is zero.
     *
     * @throws ResourceNotFoundException if the portfolio is not owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public FixedIncomeSummaryResponse summary(Long portfolioId, String userSub) {
        support.requireOwnedPortfolio(portfolioId, userSub);
        LocalDate today = LocalDate.now();

        List<DepositHolding> deposits = depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(portfolioId);
        List<BondHolding> bonds = bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId);

        BigDecimal depositValue = BigDecimal.ZERO;
        BigDecimal depositCost = BigDecimal.ZERO;
        for (DepositHolding d : deposits) {
            // Mirror history()'s per-holding FX-gap tolerance: a foreign deposit whose value (today's rate) or
            // cost (its start-date rate) has no FX observation must degrade just that one leg to zero, NOT 500 the
            // whole headline. The two legs convert at DIFFERENT dates, so each is guarded independently.
            try {
                depositValue = depositValue.add(support.depositValueTry(d, today));
            } catch (FxRateUnavailableException ex) {
                log.debug("No FX rate for deposit {} value at {} — omitting from headline value", d.getId(), today);
            }
            try {
                depositCost = depositCost.add(support.depositCostTry(d));
            } catch (FxRateUnavailableException ex) {
                log.debug("No FX rate for deposit {} cost at {} — omitting from headline cost",
                        d.getId(), d.getStartDate());
            }
        }

        BigDecimal bondValue = BigDecimal.ZERO;
        BigDecimal bondCost = BigDecimal.ZERO;
        for (BondHolding b : bonds) {
            // A sold bond is valued at its FROZEN exit price (its realized proceeds), NOT today's live clean
            // price — otherwise an exited position keeps drifting with the market and double-counts capital the
            // user no longer holds. This mirrors the per-row grid (BondHoldingService.toResponse), whose
            // currentValueTry is the clean (nominal) value, so the headline reconciles with the grid.
            boolean perUnit = support.isPerUnitBond(b.getBondSeriesCode());
            BigDecimal bondVal = b.isClosed()
                    ? b.currentValue(b.getExitPrice(), perUnit)
                    : bondValuationService.currentValueTry(b, today);
            bondValue = bondValue.add(bondVal);
            bondCost = bondCost.add(b.entryValue(perUnit));
        }

        BigDecimal totalValue = FixedIncomeValuationSupport.scaled(depositValue.add(bondValue));
        BigDecimal totalCost = FixedIncomeValuationSupport.scaled(depositCost.add(bondCost));
        BigDecimal totalPnl = FixedIncomeValuationSupport.scaled(totalValue.subtract(totalCost));
        BigDecimal pnlPercent = totalCost.signum() == 0 ? null
                : totalPnl.multiply(HUNDRED).divide(totalCost, MoneyScale.PRICE, RoundingMode.HALF_UP);
        // Coupon cash already received across all bonds — realized income on top of the clean-price value, summed
        // exactly the way the history series' last point credits it (so the chart endpoint and this headline agree).
        BigDecimal couponsReceived = FixedIncomeValuationSupport.scaled(totalBondCouponsReceived(bonds, today));

        return new FixedIncomeSummaryResponse(totalCost, totalValue, totalPnl, pnlPercent,
                deposits.size(), bonds.size(),
                FixedIncomeValuationSupport.scaled(depositValue), FixedIncomeValuationSupport.scaled(bondValue),
                couponsReceived, today);
    }

    /**
     * Day-by-day TRY value series for the view — delegated to {@link FixedIncomeHistoryService}, which owns the
     * time-series build. Kept on this service so a caller has one read-side entry point for both the headline
     * and the chart; the delegate target carries the read-only transaction and the ownership check.
     *
     * @throws ResourceNotFoundException if the portfolio is not owned by {@code userSub}
     */
    public List<FixedIncomeHistoryPoint> history(Long portfolioId, String userSub, String period) {
        return historyService.history(portfolioId, userSub, period);
    }

    /**
     * Cumulative TRY coupon cash received across all bonds as of {@code today}, reusing the same coupon-event
     * engine the history series builds (each past coupon priced at its own date's .ORAN, CPI/gold coupons on the
     * indexed value at the coupon date). Summing {@code headMap(today, true)} per bond yields exactly the history
     * series' last-point coupon credit, so the headline and the chart reconcile. Zero when there are no bonds.
     */
    private BigDecimal totalBondCouponsReceived(List<BondHolding> bonds, LocalDate today) {
        if (bonds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, Bond> bondCatalog = support.loadBondCatalog(bonds);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> bondPriceSeries = support.loadBondPriceSeries(bonds);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> couponEvents =
                bondCouponEventBuilder.loadBondCouponEvents(bonds, bondCatalog, bondPriceSeries);
        BigDecimal total = BigDecimal.ZERO;
        for (BondHolding b : bonds) {
            NavigableMap<LocalDate, BigDecimal> events = b.getId() == null ? null : couponEvents.get(b.getId());
            if (events == null) {
                continue;
            }
            for (BigDecimal amount : events.headMap(today, true).values()) {
                total = total.add(amount);
            }
        }
        return total;
    }
}
