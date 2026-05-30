package com.finance.portfolio.startup;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.service.PortfolioBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Seeds historical snapshots for portfolios that reached the database outside the app's lot-change
 * flow — primarily the clone-and-run demo seed, whose positions are inserted via SQL and so never
 * fire a {@link PortfolioBackfillService.LotChangedEvent}. Without this such a portfolio renders an
 * empty performance curve until the next nightly snapshot. Runs once on startup, off the main thread,
 * and is a no-op when every portfolio already has history.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotBootstrap {

    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioBackfillService backfillService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void backfillPortfoliosWithoutHistory() {
        List<Long> portfolioIds = positionRepository.findPortfolioIdsWithoutSnapshots();
        if (portfolioIds.isEmpty()) return;
        log.info("Bootstrap snapshot backfill for {} portfolio(s) without history: {}", portfolioIds.size(), portfolioIds);
        for (Long portfolioId : portfolioIds) {
            try {
                LocalDate from = earliestEntryDate(portfolioId);
                if (from != null) backfillService.backfillEntirePortfolio(portfolioId, from);
            } catch (Exception e) {
                log.warn("Bootstrap backfill failed for portfolio {}: {}", portfolioId, e.getMessage(), e);
            }
        }
    }

    private LocalDate earliestEntryDate(Long portfolioId) {
        LocalDate spot = positionRepository.findByPortfolioId(portfolioId).stream()
                .map(PortfolioPosition::getEntryDate)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate derivative = derivativePositionRepository.findByPortfolioId(portfolioId).stream()
                .map(DerivativePosition::getEntryDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (spot == null) return derivative;
        if (derivative == null) return spot;
        return spot.isBefore(derivative) ? spot : derivative;
    }
}
