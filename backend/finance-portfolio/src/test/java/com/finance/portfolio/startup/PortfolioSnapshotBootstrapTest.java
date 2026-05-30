package com.finance.portfolio.startup;

import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.service.PortfolioBackfillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotBootstrapTest {

    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private DerivativePositionRepository derivativePositionRepository;
    @Mock private PortfolioBackfillService backfillService;

    @InjectMocks private PortfolioSnapshotBootstrap bootstrap;

    @Test
    void shouldDoNothing_whenNoPortfolioLacksHistory() {
        // Arrange
        when(positionRepository.findPortfolioIdsWithoutSnapshots()).thenReturn(List.of());

        // Act
        bootstrap.backfillPortfoliosWithoutHistory();

        // Assert
        verify(backfillService, never()).backfillEntirePortfolio(any(), any());
    }

    @Test
    void shouldBackfillFromEarliestEntry_whenPortfolioLacksHistory() {
        // Arrange
        LocalDate earliest = LocalDate.now().minusDays(40);
        when(positionRepository.findPortfolioIdsWithoutSnapshots()).thenReturn(List.of(7L));
        when(positionRepository.findByPortfolioId(7L)).thenReturn(List.of(
                lot(earliest.plusDays(10).atStartOfDay()),
                lot(earliest.atStartOfDay())));
        when(derivativePositionRepository.findByPortfolioId(7L)).thenReturn(List.of());

        // Act
        bootstrap.backfillPortfoliosWithoutHistory();

        // Assert
        verify(backfillService).backfillEntirePortfolio(7L, earliest);
    }

    @Test
    void shouldContinueToNextPortfolio_whenOneBackfillFails() {
        // Arrange
        LocalDate day = LocalDate.now().minusDays(20);
        when(positionRepository.findPortfolioIdsWithoutSnapshots()).thenReturn(List.of(1L, 2L));
        when(positionRepository.findByPortfolioId(anyLong())).thenReturn(List.of(lot(day.atStartOfDay())));
        when(derivativePositionRepository.findByPortfolioId(anyLong())).thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(backfillService).backfillEntirePortfolio(eq(1L), any());

        // Act
        bootstrap.backfillPortfoliosWithoutHistory();

        // Assert
        verify(backfillService).backfillEntirePortfolio(2L, day);
    }

    private static PortfolioPosition lot(LocalDateTime entryDate) {
        return PortfolioPosition.builder()
                .portfolioId(1L)
                .assetType(AssetType.STOCK)
                .assetCode("THYAO.IS")
                .quantity(new BigDecimal("10"))
                .entryDate(entryDate)
                .entryPrice(new BigDecimal("100"))
                .build();
    }
}
