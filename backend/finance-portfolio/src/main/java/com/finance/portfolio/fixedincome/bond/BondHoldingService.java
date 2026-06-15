package com.finance.portfolio.fixedincome.bond;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.bond.util.BondCouponFrequencyDetector;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.request.BondHoldingRequest;
import com.finance.portfolio.dto.response.BondCouponScheduleEntry;
import com.finance.portfolio.dto.response.BondHoldingResponse;
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
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Write/read commands for hypothetical Türkiye Hazine bond (tahvil/bono) holdings. Mirrors the spot-lot
 * service's ownership discipline exactly: every method resolves the portfolio via
 * {@link PortfolioRepository#findByIdAndUserSub(Long, String)} first (so a holding id is never trusted
 * without proving the caller owns its portfolio), then asserts the loaded holding's {@code portfolioId}
 * matches. Bonds are ALWAYS TRY — no currency conversion happens here. The ISIN is denormalized onto the
 * holding at add time from the resolved market {@link Bond}, and each holding is valued through
 * {@link BondValuationService}.
 *
 * <p>Bond read seam: this injects the market {@link BondRepository} directly (the same way
 * {@link BondValuationService} injects {@code BondRateHistoryRepository}). {@code findById(seriesCode)} is
 * an authoritative, persistent existence check keyed on the series-code primary key — unlike
 * {@code BondQueryService.getByCode}, which is snapshot-cache-backed and would miss any bond not currently
 * cached and throw a market-domain error instead of the portfolio validation key.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class BondHoldingService {

    private final PortfolioRepository portfolioRepository;
    private final BondHoldingRepository bondHoldingRepository;
    private final BondRepository bondRepository;
    private final BondValuationService bondValuationService;
    private final BondCouponService bondCouponService;
    private final BondRateHistoryRepository bondRateHistoryRepository;
    private final PortfolioProperties portfolioProperties;

    @Transactional(readOnly = true)
    public List<BondHoldingResponse> list(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        LocalDate asOf = LocalDate.now();
        return bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId).stream()
                .map(holding -> toResponse(holding, asOf))
                .toList();
    }

    /**
     * Adds a bond holding under an owned portfolio. The series code is resolved against the market bond
     * catalog (existence + maturity validation), and its ISIN is denormalized onto the holding so later
     * valuation never has to re-resolve the bond.
     */
    @Transactional
    public BondHoldingResponse add(Long portfolioId, String userSub, BondHoldingRequest request) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userSub);
        // Bonds are fixed-income and belong only in a FIXED portfolio. Gated AFTER the ownership load so an
        // unowned portfolio still 404s ahead of this type check (no existence leak).
        portfolio.requireType(PortfolioType.FIXED);
        Bond bond = bondRepository.findById(request.bondSeriesCode()).orElse(null);
        BondValidator.validate(request, bond, portfolioProperties.getBondLimits());
        BondHolding holding = BondHolding.builder()
                .portfolio(portfolio)
                .bondSeriesCode(bond.getSeriesCode())
                .bondIsin(bond.getIsinCode())
                .quantity(request.quantity())
                .entryPrice(request.entryPrice())
                .entryDate(request.entryDate())
                .couponRateOverride(request.couponRateOverride())
                .couponPaymentFrequency(resolveFrequency(request.couponPaymentFrequency(), bond))
                .build();
        BondHolding saved = bondHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    /** Edits an owned holding's quantity, entry price and entry date; re-validates against the bond record. */
    @Transactional
    public BondHoldingResponse update(Long portfolioId, Long holdingId, String userSub, BondHoldingRequest request) {
        BondHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        // The series/ISIN is immutable after add (the UI disables the picker on edit). Reject a swapped series
        // code outright: otherwise entryDate would be validated against a DIFFERENT bond's maturity than the one
        // actually held, letting a post-maturity entryDate slip past the alreadyMatured guard for the real bond.
        if (!holding.getBondSeriesCode().equals(request.bondSeriesCode())) {
            throw new BusinessException("error.portfolio.bond.seriesImmutable");
        }
        Bond bond = bondRepository.findById(request.bondSeriesCode()).orElse(null);
        BondValidator.validate(request, bond, portfolioProperties.getBondLimits());
        holding.update(request.quantity(), request.entryPrice(), request.entryDate(),
                request.couponRateOverride(), resolveFrequency(request.couponPaymentFrequency(), bond));
        BondHolding saved = bondHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    /** Closes an owned holding by recording an exit (sell) at {@code exitPrice} TRY per 100 nominal on {@code exitDate}. */
    @Transactional
    public BondHoldingResponse sell(Long portfolioId, Long holdingId, String userSub,
                                    LocalDate exitDate, BigDecimal exitPrice) {
        BondHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        if (holding.isClosed()) {
            throw new BusinessException("error.portfolio.bond.alreadyClosed");
        }
        if (exitDate.isBefore(holding.getEntryDate())) {
            throw new BusinessException("error.portfolio.bond.exitBeforeEntry");
        }
        if (exitDate.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.bond.exitDateInFuture");
        }
        // A free sale may happen any day from entry up to and INCLUDING maturity, but never after it — past
        // maturity the bond is redeemed at par, not sold. Enforced server-side (not just in the dialog) so a
        // direct API call can't record a post-maturity exit, mirroring the price-bound guard below.
        Bond resolved = bondRepository.findById(holding.getBondSeriesCode()).orElse(null);
        if (resolved != null && resolved.getMaturityEnd() != null && exitDate.isAfter(resolved.getMaturityEnd())) {
            throw new BusinessException("error.portfolio.bond.exitAfterMaturity");
        }
        // The exit price must clear the same TRY clean-price bounds as add/update: a negative/zero/absurd value
        // would otherwise persist and produce a corrupted negative realized value and PnL in the grid + headline.
        BondValidator.validatePrice(exitPrice, portfolioProperties.getBondLimits());
        holding.closeWith(exitDate, exitPrice);
        BondHolding saved = bondHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    /** Re-opens a closed (sold) holding, clearing its exit and returning it to held state. */
    @Transactional
    public BondHoldingResponse reopen(Long portfolioId, Long holdingId, String userSub) {
        BondHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        if (!holding.isClosed()) {
            throw new BusinessException("error.portfolio.bond.notClosed");
        }
        holding.reopen();
        BondHolding saved = bondHoldingRepository.save(holding);
        return toResponse(saved, LocalDate.now());
    }

    @Transactional
    public void delete(Long portfolioId, Long holdingId, String userSub) {
        BondHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        bondHoldingRepository.delete(holding);
    }

    /**
     * The owned holding's full coupon schedule (one entry per coupon date, issue→maturity), each priced at the
     * per-period rate in effect on its own date — historical resets for a TLREF/auction floater. This is the
     * SINGLE backend source for the per-coupon breakdown (the UI no longer reconstructs it), and it reconciles
     * with the {@code couponsReceivedTry} total since both share {@link BondCouponService}'s coupon-date stepping.
     * A discount bill or a CPI bond has no per-100 coupon schedule and returns empty.
     */
    @Transactional(readOnly = true)
    public List<BondCouponScheduleEntry> couponSchedule(Long portfolioId, Long holdingId, String userSub) {
        BondHolding holding = loadOwnedHolding(portfolioId, holdingId, userSub);
        Bond bond = bondRepository.findById(holding.getBondSeriesCode()).orElse(null);
        BondType type = bond == null ? null : bond.getBondType();
        // Only a discount bill (no coupon) has no schedule. CPI and gold DO pay a periodic coupon — a small real
        // coupon on the inflation-indexed value, a rental on the gold value — so they now produce a schedule too,
        // each coupon converted against that bond's indexed/gold value AT ITS OWN DATE (see couponBase below).
        if (type == null || type == BondType.DISCOUNTED) {
            return List.of();
        }
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
        NavigableMap<LocalDate, BigDecimal> priceSeries = indexed ? loadPriceHistory(holding.getBondIsin()) : null;
        LocalDate asOf = holding.isClosed() ? holding.getExitDate() : LocalDate.now();
        return bondCouponService.schedule(rateMap, fallbackPerPeriod, frequency,
                        bond.getMaturityStart(), bond.getMaturityEnd(), holding.getEntryDate(), asOf).stream()
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

    private Portfolio requireOwnedPortfolio(Long portfolioId, String userSub) {
        return portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
    }

    private BondHolding loadOwnedHolding(Long portfolioId, Long holdingId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        BondHolding holding = bondHoldingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.position.notFound", holdingId));
        if (!holding.getPortfolioId().equals(portfolioId)) {
            throw new BusinessException("error.portfolio.position.notInPortfolio", portfolioId);
        }
        return holding;
    }

    /**
     * The coupon payment frequency to store: the holder's explicit choice when given, otherwise a type-based
     * default ({@link CouponFrequency#defaultFor}) so a TLREF floater defaults to quarterly and a discount bill
     * to zero-coupon rather than blanket semi-annual.
     */
    private CouponFrequency resolveFrequency(String requested, Bond bond) {
        if (requested != null && !requested.isBlank()) {
            return CouponFrequency.fromNullable(requested);
        }
        // No explicit choice: for a TLREF/auction FLOATER, infer the cadence from the periodic ex-coupon price
        // drops in its history (its sawtooth) — the actual per-bond cadence, more accurate than a type-based
        // default — and fall back to the BondType default when the history is too short/noisy to read a regular
        // drop spacing. CPI/fixed/discount bonds have no clean coupon-drop signal, so they keep the default.
        BondType type = bond == null ? null : bond.getBondType();
        if (type != null && type.isParFloater()) {
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
    private BondHoldingResponse toResponse(BondHolding holding, LocalDate asOf) {
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
        BigDecimal couponsReceivedTry = bondCouponService.per100ToTry(couponsPaid.totalPer100(), couponBase);

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
