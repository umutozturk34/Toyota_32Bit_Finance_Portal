package com.finance.market.bond.service;

import com.finance.common.exception.BusinessException;
import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondDataServiceTest {

    @Mock private BondSnapshotService bondSnapshotService;
    @Mock private BondRateHistoryService bondRateHistoryService;

    private BondDataService service;

    @BeforeEach
    void setUp() {
        service = new BondDataService(bondSnapshotService, bondRateHistoryService);
    }

    private BondSerieDto serieDto(String isin) {
        return new BondSerieDto(isin, "TP." + isin, "Bond " + isin,
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
    }

    private BondSnapshotDto snapshotDto(String series, String isin) {
        return new BondSnapshotDto(series, isin, BigDecimal.ONE, BigDecimal.ONE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), series);
    }

    @Test
    void updateBonds_processesEverySnapshot_whenDataReturned() {
        BondSerieDto s = serieDto("ISIN1");
        BondSnapshotDto snap1 = snapshotDto("S1", "ISIN1");
        BondSnapshotDto snap2 = snapshotDto("S2", "ISIN2");
        when(bondSnapshotService.fetchAndFilterSeries()).thenReturn(List.of(s));
        when(bondSnapshotService.fetchSnapshotData(List.of(s))).thenReturn(List.of(snap1, snap2));

        service.updateBonds();

        verify(bondRateHistoryService).processSingleBond(eq(snap1), any());
        verify(bondRateHistoryService).processSingleBond(eq(snap2), any());
    }

    @Test
    void updateBonds_raises_whenSnapshotDataEmpty() {
        when(bondSnapshotService.fetchAndFilterSeries()).thenReturn(List.of(serieDto("ISIN1")));
        when(bondSnapshotService.fetchSnapshotData(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateBonds())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No bond snapshot data");
        verify(bondRateHistoryService, never()).processSingleBond(any(), any());
    }

    @Test
    void updateBonds_continuesAfterPerBondFailure_andProcessesNextItem() {
        BondSnapshotDto snap1 = snapshotDto("S1", "ISIN1");
        BondSnapshotDto snap2 = snapshotDto("S2", "ISIN2");
        when(bondSnapshotService.fetchAndFilterSeries()).thenReturn(List.of(serieDto("ISIN1")));
        when(bondSnapshotService.fetchSnapshotData(any())).thenReturn(List.of(snap1, snap2));
        org.mockito.Mockito.doThrow(new RuntimeException("first failed"))
                .when(bondRateHistoryService).processSingleBond(eq(snap1), any());

        service.updateBonds();

        verify(bondRateHistoryService, times(1)).processSingleBond(eq(snap1), any());
        verify(bondRateHistoryService, times(1)).processSingleBond(eq(snap2), any());
    }
}
