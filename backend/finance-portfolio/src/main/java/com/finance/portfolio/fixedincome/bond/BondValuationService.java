package com.finance.portfolio.fixedincome.bond;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Values a {@link BondHolding} from the forward-filled clean price in {@code bond_rate_history}: the
 * latest stored price (TRY per 100 nominal) with {@code rate_date <= asOf} for the holding's ISIN.
 * Bonds are ALWAYS TRY (never FX-converted). When no price row exists at or before {@code asOf} (e.g. a
 * brand-new ISIN, or a date that predates the scraped history) valuation falls back to the holding's
 * {@code entryPrice} rather than failing, so a portfolio total is never blocked by a single missing series.
 *
 * <p>A zero-coupon DISCOUNT bill (BONO) is the exception: it pays no coupon and its scraped secondary quote
 * is sparse/noisy, so it is valued by deterministic pull-to-par accretion
 * ({@link BondHolding#accretedCleanPrice}) — the price climbs straight-line from the entry price to par (100)
 * at maturity — rather than from {@code bond_rate_history}. This guarantees a bill bought below par redeems at
 * exactly 100 on every surface (grid, summary, history, snapshot) that values through this service.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class BondValuationService {

    private static final BigDecimal PAR = new BigDecimal("100");

    private final BondRateHistoryRepository bondRateHistoryRepository;
    private final BondRepository bondRepository;

    /**
     * Clean price (TRY per 100 nominal) for the holding as of {@code asOf}. SETTLEMENT: at or after maturity a
     * non-CPI bond is redeemed at PAR (100) — its face is repaid, so the clean price is par regardless of any
     * stale scraped quote (a CPI bond redeems at its indexed reference value, which the stored index price
     * already carries, so it keeps the history path). Before maturity: a discount bill is the pull-to-par
     * accreted price; everything else is the latest {@code bond_rate_history} price with {@code rate_date <= asOf}
     * for the holding's ISIN, falling back to {@link BondHolding#getEntryPrice()} when no such row exists.
     */
    public BigDecimal cleanPriceTry(BondHolding holding, LocalDate asOf) {
        Bond bond = bondRepository.findById(holding.getBondSeriesCode()).orElse(null);
        BondType type = bond == null ? null : bond.getBondType();
        LocalDate maturityEnd = bond == null ? null : bond.getMaturityEnd();
        boolean cpiLinked = type != null && type.isCpiLinked();
        boolean goldLinked = type != null && type.isGoldLinked();
        // Par redemption applies to plain TRY bonds only: a CPI bond redeems at its indexed value and a gold bond
        // at its gold-content value (both carried by the stored price), so neither settles at par 100.
        if (!cpiLinked && !goldLinked && maturityEnd != null && !asOf.isBefore(maturityEnd)) {
            return PAR;
        }
        if (type == BondType.DISCOUNTED) {
            return holding.accretedCleanPrice(maturityEnd, asOf);
        }
        String isin = holding.getBondIsin();
        if (isin == null || isin.isBlank()) {
            return holding.getEntryPrice();
        }
        return bondRateHistoryRepository
                .findFirstByIsinCodeAndRateDateLessThanEqualOrderByRateDateDesc(isin, asOf)
                .map(row -> row.getPrice())
                .filter(price -> price != null)
                .orElseGet(() -> {
                    log.debug("No bond_rate_history price isin={} onOrBefore={} — falling back to entryPrice",
                            isin, asOf);
                    return holding.getEntryPrice();
                });
    }

    /**
     * Market value in TRY for the holding as of {@code asOf}: clean price × quantity ÷ 100, or — for a gold-linked
     * (per-certificate) bond — clean price × quantity (no ÷ 100), since gold is quoted per unit, not per 100.
     */
    public BigDecimal currentValueTry(BondHolding holding, LocalDate asOf) {
        Bond bond = bondRepository.findById(holding.getBondSeriesCode()).orElse(null);
        boolean perUnit = bond != null && bond.getBondType() != null && bond.getBondType().isPerUnit();
        return holding.currentValue(cleanPriceTry(holding, asOf), perUnit);
    }
}
