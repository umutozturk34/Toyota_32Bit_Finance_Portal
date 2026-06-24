package com.finance.portfolio.fixedincome.bond;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.repository.BondRepository;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.request.BondHoldingRequest;
import com.finance.portfolio.dto.response.BondCouponScheduleEntry;
import com.finance.portfolio.dto.response.BondHoldingResponse;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioType;
import com.finance.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Write/read commands for hypothetical Türkiye Hazine bond (tahvil/bono) holdings. Mirrors the spot-lot
 * service's ownership discipline exactly: every method resolves the portfolio via
 * {@link PortfolioRepository#findByIdAndUserSub(Long, String)} first (so a holding id is never trusted
 * without proving the caller owns its portfolio), then asserts the loaded holding's {@code portfolioId}
 * matches. Bonds are ALWAYS TRY — no currency conversion happens here. The ISIN is denormalized onto the
 * holding at add time from the resolved market {@link Bond}, and each holding is projected to a response
 * (and its coupon schedule built) through {@link BondHoldingProjection}.
 *
 * <p>Bond read seam: this injects the market {@link BondRepository} directly. {@code findById(seriesCode)} is
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
    private final BondHoldingProjection projection;
    private final PortfolioProperties portfolioProperties;

    @Transactional(readOnly = true)
    public List<BondHoldingResponse> list(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        LocalDate asOf = LocalDate.now();
        return bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId).stream()
                .map(holding -> projection.toResponse(holding, asOf))
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
                .couponPaymentFrequency(projection.resolveFrequency(request.couponPaymentFrequency(), bond))
                .build();
        BondHolding saved = bondHoldingRepository.save(holding);
        return projection.toResponse(saved, LocalDate.now());
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
                request.couponRateOverride(), projection.resolveFrequency(request.couponPaymentFrequency(), bond));
        BondHolding saved = bondHoldingRepository.save(holding);
        return projection.toResponse(saved, LocalDate.now());
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
        return projection.toResponse(saved, LocalDate.now());
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
        return projection.toResponse(saved, LocalDate.now());
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
        LocalDate asOf = holding.isClosed() ? holding.getExitDate() : LocalDate.now();
        return projection.buildCouponSchedule(holding, bond, asOf);
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
}
