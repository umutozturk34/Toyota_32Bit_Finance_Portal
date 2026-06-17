package com.finance.portfolio.derivative.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.MarketDataNotReadyException;
import com.finance.common.market.MarketDataReadiness;
import com.finance.market.viop.config.ViopProperties;
import com.finance.portfolio.config.PortfolioProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pre-trade guards for VIOP derivative lots — entry-date floor, TRY notional cap and market-data readiness —
 * extracted from {@code DerivativePositionService} so the command service stays focused on mutation and
 * orchestration. Each guard throws on violation and returns silently otherwise.
 */
@Component
@RequiredArgsConstructor
public class DerivativePositionValidator {

    private final ViopProperties viopProperties;
    private final PortfolioProperties portfolioProperties;
    private final ObjectProvider<MarketDataReadiness> marketDataReadiness;

    /**
     * Rejects an entry date older than the VIOP history window ({@code today − max-history-years}). VIOP candle
     * data only goes back that far, so an earlier entry has no price/candles to value against — the same
     * floor the contract chart is clamped to. Mirrors the spot lot's min-entry-date guard.
     */
    public void requireEntryWithinViopHistory(LocalDate entryDate) {
        LocalDate floor = LocalDate.now().minusYears(viopProperties.maxHistoryYears());
        if (entryDate != null && entryDate.isBefore(floor)) {
            throw new BusinessException("error.portfolio.lot.entryDateTooOld", floor);
        }
    }

    /**
     * Rejects a VIOP lot whose TRY notional (entry price × contract size × lots) would overflow the
     * numeric(23,8) snapshot money columns — the same product the snapshot persists, so an absurd lot would
     * otherwise abort the snapshot batch and break the portfolio's whole chart. Mirrors the spot lot-value cap.
     */
    public void requireNotionalWithinCap(BigDecimal entryPriceTry, BigDecimal contractSize, BigDecimal quantityLot) {
        BigDecimal max = portfolioProperties.getLotLimits().getMaxLotValueTry();
        if (entryPriceTry == null || quantityLot == null || max == null) return;
        BigDecimal size = contractSize != null ? contractSize : BigDecimal.ONE;
        if (entryPriceTry.multiply(size).multiply(quantityLot).compareTo(max) > 0) {
            throw new BusinessException("error.portfolio.lot.valueTooHigh", max);
        }
    }

    /** @throws MarketDataNotReadyException (HTTP 503) if the cold-start market-data load has not finished yet. */
    public void requireMarketDataReady() {
        MarketDataReadiness readiness = marketDataReadiness.getIfAvailable();
        if (readiness != null && !readiness.isReady()) {
            throw new MarketDataNotReadyException("error.market.dataNotReady");
        }
    }
}
