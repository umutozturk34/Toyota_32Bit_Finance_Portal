package com.finance.market.bond.service;

import com.finance.common.exception.BusinessException;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        BondProperties props = new BondProperties();
        props.setHistoryBatchSize(2);
        service = new BondDataService(bondSnapshotService, bondRateHistoryService, props);
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
    void updateBonds_chunksSnapshots_andCallsProcessBatchPerChunk() {
        BondSerieDto s = serieDto("ISIN1");
        BondSnapshotDto snap1 = snapshotDto("S1", "ISIN1");
        BondSnapshotDto snap2 = snapshotDto("S2", "ISIN2");
        BondSnapshotDto snap3 = snapshotDto("S3", "ISIN3");
        when(bondSnapshotService.fetchAndFilterSeries()).thenReturn(List.of(s));
        when(bondSnapshotService.fetchSnapshotData(List.of(s))).thenReturn(List.of(snap1, snap2, snap3));

        service.updateBonds();

        ArgumentCaptor<List<BondSnapshotDto>> chunkCap = ArgumentCaptor.forClass(List.class);
        verify(bondRateHistoryService, times(2)).processBatch(chunkCap.capture(), any());
        List<List<BondSnapshotDto>> chunks = chunkCap.getAllValues();
        assertThat(chunks.get(0)).containsExactly(snap1, snap2);
        assertThat(chunks.get(1)).containsExactly(snap3);
    }

    @Test
    void updateBonds_raises_whenSnapshotDataEmpty() {
        when(bondSnapshotService.fetchAndFilterSeries()).thenReturn(List.of(serieDto("ISIN1")));
        when(bondSnapshotService.fetchSnapshotData(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateBonds())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No bond snapshot data");
        verify(bondRateHistoryService, never()).processBatch(any(), any());
    }

    @Test
    void updateBonds_continuesToNextBatch_whenOneBatchFails() {
        BondSnapshotDto snap1 = snapshotDto("S1", "ISIN1");
        BondSnapshotDto snap2 = snapshotDto("S2", "ISIN2");
        BondSnapshotDto snap3 = snapshotDto("S3", "ISIN3");
        when(bondSnapshotService.fetchAndFilterSeries()).thenReturn(List.of(serieDto("ISIN1")));
        when(bondSnapshotService.fetchSnapshotData(any())).thenReturn(List.of(snap1, snap2, snap3));
        org.mockito.Mockito.doThrow(new RuntimeException("first batch failed"))
                .doNothing()
                .when(bondRateHistoryService).processBatch(any(), any());

        service.updateBonds();

        verify(bondRateHistoryService, times(2)).processBatch(any(), any());
    }
}
