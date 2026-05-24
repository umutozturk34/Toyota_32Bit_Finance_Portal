package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSeriesProviderTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;

    @InjectMocks
    private PortfolioSeriesProvider provider;

    private PortfolioDailySnapshot snapshot(LocalDate date, BigDecimal value) {
        return PortfolioDailySnapshot.builder()
                .snapshotDate(date)
                .totalValueTry(value)
                .build();
    }

    @Test
    void shouldRaiseNotFound_whenPortfolioMissing() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.dailyValueSeries(7L, "u", from, to))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
    }

    @Test
    void shouldMapSnapshotsToHistoryPoints_whenSnapshotsExist() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of(
                        snapshot(LocalDate.of(2024, 2, 1), new BigDecimal("100")),
                        snapshot(LocalDate.of(2024, 3, 1), new BigDecimal("150"))));

        List<HistoryPoint> series = provider.dailyValueSeries(7L, "u", from, to);

        assertThat(series).hasSize(2);
        assertThat(series.get(0).date()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(series.get(0).value()).isEqualByComparingTo("100");
        assertThat(series.get(1).value()).isEqualByComparingTo("150");
    }

    @Test
    void shouldFilterOutSnapshotsWithNullFields_whenMappingResults() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of(
                        snapshot(null, new BigDecimal("100")),
                        snapshot(LocalDate.of(2024, 3, 1), null),
                        snapshot(LocalDate.of(2024, 4, 1), new BigDecimal("200"))));

        List<HistoryPoint> series = provider.dailyValueSeries(7L, "u", from, to);

        assertThat(series).hasSize(1);
        assertThat(series.get(0).date()).isEqualTo(LocalDate.of(2024, 4, 1));
    }

    @Test
    void shouldReturnEmptySeries_whenNoSnapshotsFound() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of());

        List<HistoryPoint> series = provider.dailyValueSeries(7L, "u", from, to);

        assertThat(series).isEmpty();
    }
}
