package com.finance.portfolio.service.summary;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Loads the per-currency (USD/EUR) FX rate series a portfolio's allocation needs to convert cost/realized legs at
 * their own entry/exit-date FX. Shared by both the current-value and realized-PnL allocators because both convert
 * the same cost basis at the same per-date rates, so the window-floor logic (reach back to the OLDEST entry across
 * open AND closed positions, not just exits) lives in one place rather than being duplicated per calculator.
 */
@Component
@RequiredArgsConstructor
class AllocationFxFrameLoader {

    private static final List<String> FRAME_CURRENCIES = List.of("USD", "EUR");

    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final HistoricalPricingPort historicalPricingPort;

    Map<String, TreeMap<LocalDate, BigDecimal>> loadFxFrameSeries(Long portfolioId) {
        LocalDate today = LocalDate.now();
        // Series must reach back to the oldest entry date across ALL positions/derivatives — open AND
        // closed, not just the oldest exit date. The cost basis is converted at each position's
        // entry-date FX (open derivatives carry per-date EQUITY frames too), so an entry that predates
        // the loaded window has no floor rate and silently falls back to today's spot for its cost leg.
        LocalDate oldest = positionRepository.findByPortfolioId(portfolioId).stream()
                .flatMap(p -> Stream.of(
                        p.getExitDate() != null ? p.getExitDate().toLocalDate() : null,
                        p.getEntryDate() != null ? p.getEntryDate().toLocalDate() : null))
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(today);
        LocalDate oldestDerivativeEntry = derivativePositionRepository.findByPortfolioId(portfolioId).stream()
                .map(DerivativePosition::getEntryDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (oldestDerivativeEntry != null && oldestDerivativeEntry.isBefore(oldest)) {
            oldest = oldestDerivativeEntry;
        }
        Map<String, TreeMap<LocalDate, BigDecimal>> series = new LinkedHashMap<>();
        for (String currency : FRAME_CURRENCIES) {
            Map<LocalDate, BigDecimal> raw = historicalPricingPort.getPriceSeries(
                    MarketType.FOREX, currency, oldest.minusDays(14), today.plusDays(1));
            series.put(currency, raw != null && !raw.isEmpty() ? new TreeMap<>(raw) : new TreeMap<>());
        }
        return series;
    }
}
