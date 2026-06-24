package com.finance.portfolio.fixedincome.bond;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.bond.util.BondCouponFrequencyDetector;
import com.finance.portfolio.dto.response.BondCouponScheduleEntry;
import com.finance.portfolio.dto.response.BondHoldingResponse;
import com.finance.portfolio.model.MoneyScale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Read-side projection collaborator for bond holdings: turns a persisted {@link BondHolding} into the
 * {@link BondHoldingResponse} grid row and the per-coupon {@link BondCouponScheduleEntry} breakdown, and
 * resolves the coupon payment frequency to store on add/update.
 *
 * <p>This is the pure pricing/projection seam carved out of {@link BondHoldingService}: it depends only on the
 * market bond catalog and rate history ({@link BondRepository}, {@link BondRateHistoryRepository}) plus the
 * valuation/coupon engines ({@link BondValuationService}, {@link BondCouponService}) — never on the portfolio
 * ownership repositories or write/transaction plumbing. Keeping it separate isolates the dense coupon-base /
 * floater-reset / CPI-indexed math (which has no ownership or persistence concern) from the command service's
 * ownership-guarded CRUD, so the two evolve independently. It holds no state and is side-effect free.
 */
@Component
@RequiredArgsConstructor
public class BondHoldingProjection {

    private final BondRepository bondRepository;
    private final BondValuationService bondValuationService;
    private final BondCouponService bondCouponService;
    private final BondRateHistoryRepository bondRateHistoryRepository;

    /**
     * Builds the priced coupon schedule (one entry per coupon date, issue→maturity) for a holding: each coupon at
     * the per-period rate in effect on its date and — for a CPI/gold bond — against the indexed/gold value ON THAT
     * DATE; a plain/floater coupon rides the constant face nominal. Shared by the public coupon-schedule breakdown
     * and the {@code couponsReceivedTry} headline so the two always reconcile. A discount bill (no coupon) returns
     * empty.
     */
    List<BondCouponScheduleEntry> buildCouponSchedule(BondHolding holding, Bond bond, LocalDate asOf) {
        if (bond == null || bond.getBondType() == null || bond.getBondType() == BondType.DISCOUNTED) {
            return List.of();
        }
        BondType type = bond.getBondType();
        CouponFrequency frequency = holding.getCouponPaymentFrequency();
        boolean floating = type.isParFloater() && holding.getCouponRateOverride() == null;
        NavigableMap<LocalDate, BigDecimal> rateMap = floating ? loadCouponRateHistory(holding.getBondIsin()) : null;
        // A holder override (legacy) freezes one ANNUAL rate → its per-period equivalent is the flat fallback;
        // otherwise the bond's published per-period rate fills any gap before the history starts.
        BigDecimal fallbackPerPeriod = (holding.getCouponRateOverride() != null
                && frequency != null && frequency.paysCoupon())
                ? holding.getCouponRateOverride().divide(
                        BigDecimal.valueOf(frequency.paymentsPerYear()), MoneyScale.PRICE, RoundingMode.HALF_UP)
                : (bond == null ? null : bond.getCouponRate());
        // CPI/gold coupons ride the indexed/gold value (which grows over time), so each is converted against that
        // value on its own coupon date; a plain/floater coupon is on the constant face nominal.
        boolean indexed = type.isCpiLinked() || type.isGoldLinked();
        boolean perUnit = type.isPerUnit();
        // The price series feeds two things: the CPI/gold coupon base, AND (for a floater) the ex-coupon DROP that
        // pins each coupon's rate to the day before it — so load it whenever the bond is indexed OR floating.
        NavigableMap<LocalDate, BigDecimal> priceSeries = (indexed || floating)
                ? loadPriceHistory(holding.getBondIsin()) : null;
        return bondCouponService.schedule(rateMap, fallbackPerPeriod, frequency,
                        bond.getMaturityStart(), bond.getMaturityEnd(), holding.getEntryDate(), asOf, priceSeries).stream()
                .map(e -> {
                    BigDecimal couponBase = indexed
                            ? holding.currentValue(priceAt(priceSeries, e.date(), holding.getEntryPrice()), perUnit)
                            : holding.getQuantity().multiply(BigDecimal.valueOf(100));
                    return new BondCouponScheduleEntry(e.date(), e.ratePer100(),
                            bondCouponService.per100ToTry(e.ratePer100(), couponBase), e.status());
                })
                .toList();
    }

    /** Forward-filled price at {@code date} from the series (latest on/before), falling back to {@code fallback}. */
    private BigDecimal priceAt(NavigableMap<LocalDate, BigDecimal> series, LocalDate date, BigDecimal fallback) {
        if (series != null) {
            java.util.Map.Entry<LocalDate, BigDecimal> floor = series.floorEntry(date);
            if (floor != null && floor.getValue() != null) {
                return floor.getValue();
            }
        }
        return fallback;
    }

    /** The bond's clean-price history keyed by date (for converting a CPI/gold coupon against its dated value). */
    private NavigableMap<LocalDate, BigDecimal> loadPriceHistory(String isin) {
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        if (isin == null || isin.isBlank()) {
            return prices;
        }
        for (BondRateHistory row : bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(isin)) {
            if (row.getPrice() != null) {
                prices.put(row.getRateDate(), row.getPrice());
            }
        }
        return prices;
    }

    /**
     * The coupon payment frequency to store: the holder's explicit choice when given, otherwise a type-based
     * default ({@link CouponFrequency#defaultFor}) so a TLREF floater defaults to quarterly and a discount bill
     * to zero-coupon rather than blanket semi-annual.
     */
    CouponFrequency resolveFrequency(String requested, Bond bond) {
        if (requested != null && !requested.isBlank()) {
            return CouponFrequency.fromNullable(requested);
        }
        // No explicit choice: for a TLREF/auction FLOATER, infer the cadence from the periodic ex-coupon price
        // drops in its history (its sawtooth) — the actual per-bond cadence, more accurate than a type-based
        // default — and fall back to the BondType default when the history is too short/noisy to read a regular
        // drop spacing. CPI/fixed/discount bonds have no clean coupon-drop signal, so they keep the default.
        BondType type = bond == null ? null : bond.getBondType();
        if (bond != null && type != null && type.isParFloater()) {
            int stepMonths = BondCouponFrequencyDetector.detectStepMonths(
                    bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(bond.getIsinCode()));
            CouponFrequency detected = CouponFrequency.fromStepMonths(stepMonths);
            if (detected != null) {
                return detected;
            }
        }
        return CouponFrequency.defaultFor(type);
    }

    /**
     * The bond's published per-period coupon rate (.ORAN) over time, keyed by publication date — the reset history
     * for a floater. Rows with a null coupon (e.g. a discount bill, whose .ORAN slot holds days-to-maturity) are
     * skipped. Empty when the ISIN is missing or unscraped.
     */
    private NavigableMap<LocalDate, BigDecimal> loadCouponRateHistory(String isin) {
        NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>();
        if (isin == null || isin.isBlank()) {
            return rates;
        }
        for (BondRateHistory row : bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(isin)) {
            if (row.getCouponRate() != null) {
                rates.put(row.getRateDate(), row.getCouponRate());
            }
        }
        return rates;
    }

    /**
     * Builds the response for a holding. A closed holding reports its realized clean price/PnL (exit price);
     * an open one is valued via {@link BondValuationService#cleanPriceTry} as of {@code asOf}. PnL percent is
     * left null when cost is zero to avoid a divide-by-zero.
     *
     * <p>The market {@link Bond} is resolved once here and reused for both the display name and the static
     * coupon/maturity reference block. The resolution is best-effort: when the series no longer resolves the
     * name and every reference field are left null rather than throwing, so a delisted series never breaks the
     * grid (the holding's own valuation does not depend on this lookup).
     */
    BondHoldingResponse toResponse(BondHolding holding, LocalDate asOf) {
        BigDecimal cleanPrice = holding.isClosed()
                ? holding.getExitPrice()
                : bondValuationService.cleanPriceTry(holding, asOf);

        Bond bond = bondRepository.findById(holding.getBondSeriesCode()).orElse(null);
        // A gold-linked bond is quoted PER CERTIFICATE (value = price × quantity), every other bond per 100 nominal.
        boolean perUnit = bond != null && bond.getBondType() != null && bond.getBondType().isPerUnit();
        BigDecimal nominalValue = holding.currentValue(cleanPrice, perUnit);
        // couponRate is the EFFECTIVE ANNUAL coupon (the holder's annual override when set, else the bond's
        // published SEMI-ANNUAL rate annualized × 2). publishedCouponRate is the bond's own ANNUAL rate
        // INDEPENDENT of the override (null when the series is unresolved) so the edit form can contrast them.
        // Unlike v1, the effective coupon now FEEDS valuation: it drives the accrued-coupon (işlemiş kupon)
        // component of the dirty value below.
        BigDecimal publishedPerPeriod = bond == null ? null : bond.getCouponRate();
        CouponFrequency frequency = holding.getCouponPaymentFrequency();
        BondType bondType = bond == null ? null : bond.getBondType();
        boolean indexLinked = bondType != null && bondType.isCpiLinked();
        boolean goldLinked = bondType != null && bondType.isGoldLinked();
        // A TLREF/auction/floating-sukuk FLOATER resets its coupon each period (and a future reset must reflect), so
        // its coupon math reads the PER-PERIOD rate (.ORAN) from the bond's coupon-rate history per coupon date —
        // each period at its own rate — taking the latest publication as the current/effective rate. A
        // fixed/CPI/gold/discount bond, or a (legacy) holder override, keeps the static published rate.
        boolean floating = bondType != null && bondType.isParFloater()
                && holding.getCouponRateOverride() == null;
        NavigableMap<LocalDate, BigDecimal> floatingRates = floating
                ? loadCouponRateHistory(holding.getBondIsin())
                : null;
        BigDecimal currentPerPeriod = (floatingRates != null && !floatingRates.isEmpty())
                ? floatingRates.lastEntry().getValue()
                : publishedPerPeriod;
        BigDecimal publishedAnnual = (currentPerPeriod == null || frequency == null || !frequency.paysCoupon())
                ? null
                : currentPerPeriod.multiply(BigDecimal.valueOf(frequency.paymentsPerYear()));
        // Effective ANNUAL coupon shown in the UI: the holder's override when set, else the (possibly reset) current
        // per-period rate annualized by the holding's frequency.
        BigDecimal effectiveAnnualCoupon = holding.getCouponRateOverride() != null
                ? holding.getCouponRateOverride()
                : publishedAnnual;

        LocalDate maturityStart = bond == null ? null : bond.getMaturityStart();
        LocalDate maturityEnd = bond == null ? null : bond.getMaturityEnd();
        // The base the per-100 coupon is converted against: the bond's own VALUE (nominalValue) for a CPI bond (the
        // inflation-indexed principal) or a gold bond (the gold-content value its rental yield is paid on); the FACE
        // nominal for everything else — and since quantity is now the number of bonds (adet), each 100 nominal, the
        // face nominal is quantity × 100. per100ToTry(x, base) = x × base ÷ 100.
        BigDecimal couponBase = (indexLinked || goldLinked)
                ? nominalValue
                : holding.getQuantity().multiply(BigDecimal.valueOf(100));
        // Realized coupons: full coupons whose payment date fell between entry and now (or the exit date for a closed
        // holding), counted even for a back-dated entry — cash already received, separate from the accrued partial.
        LocalDate incomeAsOf = holding.isClosed() ? holding.getExitDate() : asOf;

        // A SALE settles at the DIRTY price: the buyer pays the clean price PLUS the işlemiş kupon accrued up to
        // the exit date, so a closed holding realizes that accrued partial as income too (not just the full coupons
        // already paid). Accrue to the exit date for a closed holding, to today for an open one. The daily accrual
        // is then zeroed for a closed holding — it stopped accruing the day it was sold.
        LocalDate accrualAsOf = holding.isClosed() ? holding.getExitDate() : asOf;
        BondCouponService.CouponAccrual accrual;
        BondCouponService.CouponsPaid couponsPaid;
        if (floating) {
            accrual = bondCouponService.accruedFloating(floatingRates, publishedPerPeriod, frequency,
                    maturityStart, maturityEnd, accrualAsOf);
            couponsPaid = bondCouponService.couponsPaidFloating(floatingRates, publishedPerPeriod, frequency,
                    maturityStart, maturityEnd, holding.getEntryDate(), incomeAsOf);
        } else {
            accrual = bondCouponService.accrued(effectiveAnnualCoupon, frequency,
                    maturityStart, maturityEnd, accrualAsOf);
            couponsPaid = bondCouponService.couponsPaid(effectiveAnnualCoupon, frequency,
                    maturityStart, maturityEnd, holding.getEntryDate(), incomeAsOf);
        }
        BigDecimal accruedCouponTry = bondCouponService.per100ToTry(accrual.accruedPer100(), couponBase);
        BigDecimal dailyCouponTry = holding.isClosed()
                ? BigDecimal.ZERO
                : bondCouponService.per100ToTry(accrual.dailyAccrualPer100(), couponBase);
        // Coupons that must be priced per-coupon (not by multiplying the summed rate by today's couponBase): CPI/gold
        // ride the indexed/gold value AT EACH COUPON'S DATE, and a par-FLOATER (TLREF/auction) ramps its rate within a
        // period so its coupon is the rate the day BEFORE the ex-coupon drop — exactly what buildCouponSchedule reads,
        // while couponsPaidFloating reads at the plain coupon date. Sum the dated schedule (the same one the grid
        // shows) so the received-coupon headline reconciles with the breakdown for all of these. A plain fixed coupon
        // has a constant rate, so the cheap totalPer100 × couponBase already matches.
        BigDecimal couponsReceivedTry = (indexLinked || goldLinked || floating)
                ? buildCouponSchedule(holding, bond, incomeAsOf).stream()
                        .filter(e -> "RECEIVED".equals(e.status()))
                        .map(BondCouponScheduleEntry::amountTry)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : bondCouponService.per100ToTry(couponsPaid.totalPer100(), couponBase);

        // currentValueTry stays the CLEAN (nominal) value so the grid/summary/history reconcile and PnL is a pure
        // mark-to-market figure; the accrued coupon (işlemiş kupon) is surfaced as its own line (accruedCouponTry)
        // and the daily accrual (dailyCouponAccrualTry) so the UI shows "nominal + işlemiş kupon" side by side.
        BigDecimal currentValue = nominalValue;
        BigDecimal cost = holding.entryValue(perUnit);
        BigDecimal pnl = currentValue.subtract(cost);
        BigDecimal pnlPercent = cost.signum() == 0
                ? null
                : pnl.multiply(BigDecimal.valueOf(100)).divide(cost, MoneyScale.PRICE, RoundingMode.HALF_UP);

        // Auto-redemption: any bond that has reached maturity while still open is treated as REDEEMED — the issuer
        // automatically repays the principal at maturity (par for a plain bond, the inflation-indexed value for a
        // CPI bond, the gold value for a gold bond — whatever cleanPriceTry returns), so the position is settled and
        // the UI presents it as closed-by-redemption (no sell action) rather than a live, sellable holding.
        boolean redeemed = !holding.isClosed() && maturityEnd != null && !asOf.isBefore(maturityEnd);

        LocalDate nextCoupon = accrual.nextCouponDate() != null
                ? accrual.nextCouponDate()
                : (bond == null ? null : bond.getNextCouponDate());
        return new BondHoldingResponse(
                holding.getId(),
                holding.getBondSeriesCode(),
                holding.getBondIsin(),
                bond == null ? null : bond.getName(),
                holding.getQuantity(),
                holding.getEntryPrice(),
                holding.getEntryDate(),
                holding.getExitDate(),
                holding.getExitPrice(),
                cleanPrice,
                currentValue,
                nominalValue,
                accruedCouponTry,
                dailyCouponTry,
                couponsReceivedTry,
                couponsPaid.count(),
                cost,
                pnl,
                pnlPercent,
                effectiveAnnualCoupon,
                publishedAnnual,
                holding.getCouponRateOverride(),
                holding.getCouponRateOverride() != null,
                maturityStart,
                maturityEnd,
                accrual.lastCouponDate(),
                nextCoupon,
                bond == null || bond.getBondType() == null ? null : bond.getBondType().name(),
                holding.getCouponPaymentFrequency().name(),
                redeemed);
    }
}
