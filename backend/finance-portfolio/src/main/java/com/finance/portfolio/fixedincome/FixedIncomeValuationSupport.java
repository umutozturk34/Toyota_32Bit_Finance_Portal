package com.finance.portfolio.fixedincome;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Currency;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.portfolio.fixedincome.bond.BondHolding;
import com.finance.portfolio.fixedincome.deposit.DepositAccrualService;
import com.finance.portfolio.fixedincome.deposit.DepositHolding;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Shared valuation primitives for the standalone "Mevduat &amp; Tahvil" (deposit + Türkiye Hazine bond) view:
 * ownership assertion, FX→TRY conversion, deposit cost/value, per-ISIN bond price-series + catalog preloading,
 * per-unit (gold-linked) resolution, and money scaling. Both the headline {@link FixedIncomeSummaryService}
 * and the value-over-time {@link FixedIncomeHistoryService} build on these, so they live here once instead of
 * being duplicated or forcing one read-side service to depend on the other.
 */
@Component
@RequiredArgsConstructor
public class FixedIncomeValuationSupport {

    private final PortfolioRepository portfolioRepository;
    private final DepositAccrualService depositAccrualService;
    private final BondRateHistoryRepository bondRateHistoryRepository;
    private final BondRepository bondRepository;
    private final CurrencyConverter currencyConverter;

    /** Resolves and asserts ownership; the portfolio entity itself is unused by callers, so nothing is returned. */
    public void requireOwnedPortfolio(Long portfolioId, String userSub) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
    }

    /** Active deposit value FX-converted to TRY at {@code asOf}; a closed deposit is already frozen in TRY. */
    public BigDecimal depositValueTry(DepositHolding deposit, LocalDate asOf) {
        BigDecimal valueNative = depositAccrualService.realizedOrAccruedValue(deposit, asOf);
        return deposit.isActive() ? toTry(valueNative, deposit.getCurrency(), asOf) : valueNative;
    }

    /** Cost basis = principal FX-converted to TRY at the deposit's entry (start) date. */
    public BigDecimal depositCostTry(DepositHolding deposit) {
        return toTry(deposit.getPrincipal(), deposit.getCurrency(), deposit.getStartDate());
    }

    /** Converts {@code amount} from {@code currencyCode} to TRY at {@code asOf}; a TRY/unknown currency is identity. */
    public BigDecimal toTry(BigDecimal amount, String currencyCode, LocalDate asOf) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        Currency from = Currency.fromCode(currencyCode);
        if (from == null || from == Currency.TRY) {
            return amount;
        }
        return currencyConverter.convertAtDate(amount, from, Currency.TRY, asOf);
    }

    /**
     * Loads each bond's full clean-price history into a date-sorted map keyed by holding id, issuing one query
     * per ISIN (vs. one per date per bond in the day loop). Rows with a null price are skipped so a later
     * {@code floorEntry} never carries a null forward.
     */
    public Map<Long, NavigableMap<LocalDate, BigDecimal>> loadBondPriceSeries(List<BondHolding> bonds) {
        Map<Long, NavigableMap<LocalDate, BigDecimal>> seriesByBondId = new HashMap<>();
        for (BondHolding bond : bonds) {
            if (bond.getId() == null) {
                continue;
            }
            NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
            String isin = bond.getBondIsin();
            if (isin != null && !isin.isBlank()) {
                bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(isin).forEach(row -> {
                    if (row.getPrice() != null) {
                        series.put(row.getRateDate(), row.getPrice());
                    }
                });
            }
            seriesByBondId.put(bond.getId(), series);
        }
        return seriesByBondId;
    }

    /**
     * Resolves each holding's market {@link Bond} once, keyed by holding id, so the day loop can read the bond
     * type/maturity without a per-date lookup. A holding whose series no longer resolves is simply absent (its
     * valuation then falls back to the forward-filled price path).
     */
    public Map<Long, Bond> loadBondCatalog(List<BondHolding> bonds) {
        Map<Long, Bond> catalog = new HashMap<>();
        for (BondHolding bond : bonds) {
            if (bond.getId() == null) {
                continue;
            }
            bondRepository.findById(bond.getBondSeriesCode())
                    .ifPresent(resolved -> catalog.put(bond.getId(), resolved));
        }
        return catalog;
    }

    /** Whether the resolved bond is quoted per certificate (gold-linked) rather than per 100 nominal. */
    public boolean isPerUnit(Bond bond) {
        return bond != null && bond.getBondType() != null && bond.getBondType().isPerUnit();
    }

    /** Resolves the bond by series code and reports whether it is per-unit (gold-linked); false when unresolved. */
    public boolean isPerUnitBond(String seriesCode) {
        return isPerUnit(bondRepository.findById(seriesCode).orElse(null));
    }

    /** Rounds a TRY amount to the shared money price scale. */
    public static BigDecimal scaled(BigDecimal value) {
        return value.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }
}
