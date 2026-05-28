package com.finance.portfolio.derivative.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.repository.ViopCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class DerivativePriceResolver {

    private final ViopCandleRepository candleRepository;
    private final HistoricalPricingPort historicalPricingPort;

    public BigDecimal resolveHistoricalPriceTry(ViopContract contract, LocalDate date) {
        BigDecimal nativePrice = resolveHistoricalPrice(contract, date);
        if (nativePrice == null) return null;
        return nativeToTryOnDate(nativePrice, contract.resolvePriceCurrency(), date);
    }

    public BigDecimal nativeToTryOnDate(BigDecimal nativePrice, String currency, LocalDate date) {
        if (nativePrice == null) return null;
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) return nativePrice;
        Map<LocalDate, BigDecimal> fxSeries = historicalPricingPort.getPriceSeries(
                MarketType.FOREX, currency.toUpperCase(),
                date.minusDays(DerivativeSnapshotMaintenance.FX_LOOKBACK_DAYS), date);
        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(fxSeries, date);
        return rate != null && rate.signum() > 0 ? nativePrice.multiply(rate) : nativePrice;
    }

    private BigDecimal resolveHistoricalPrice(ViopContract contract, LocalDate date) {
        LocalDateTime requestedEnd = date.plusDays(1).atStartOfDay();
        BigDecimal price = candleRepository
                .findFirstBySymbolAndCandleDateLessThanEqualOrderByCandleDateDesc(
                        contract.getSymbol(), requestedEnd)
                .map(c -> c.getClose())
                .orElse(null);
        if (price == null) {
            log.debug("No historical viop candle found symbol={} onOrBefore={}",
                    contract.getSymbol(), date);
        }
        return price;
    }
}
