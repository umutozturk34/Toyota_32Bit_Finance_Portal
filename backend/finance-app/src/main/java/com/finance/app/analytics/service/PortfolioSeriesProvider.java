package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioSeriesProvider {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;

    @Transactional(readOnly = true)
    public List<HistoryPoint> dailyValueSeries(Long portfolioId, String userSub, LocalDate from, LocalDate to) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.portfolio.notFound", portfolioId));
        List<PortfolioDailySnapshot> snapshots = dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(portfolioId, from, to);
        log.debug("Portfolio series fetched portfolioId={} window={}..{} points={}",
                portfolioId, from, to, snapshots.size());
        return snapshots.stream()
                .filter(s -> s.getSnapshotDate() != null && s.getTotalValueTry() != null)
                .map(s -> new HistoryPoint(s.getSnapshotDate(), s.getTotalValueTry()))
                .toList();
    }
}
