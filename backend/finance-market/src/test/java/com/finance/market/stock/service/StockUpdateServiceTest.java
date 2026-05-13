package com.finance.market.stock.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.stock.config.StockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockUpdateServiceTest {

    @Mock private StockSnapshotProcessor snapshotProcessor;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private StockProperties stockProperties;

    private StockUpdateService service;

    @BeforeEach
    void setUp() {
        when(stockProperties.getBatchMinSample()).thenReturn(10);
        service = new StockUpdateService(snapshotProcessor, trackedAssetQueryService, stockProperties);
    }

    @Test
    void getMarketType_returnsStock() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.STOCK);
    }

    @Test
    void refreshAll_throwsBusinessException_whenNoStocksTracked() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.STOCK)).thenReturn(List.of());

        assertThatThrownBy(() -> service.refreshAll())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BIST stocks");
        verify(snapshotProcessor, never()).updateOne(any());
    }

    @Test
    void refreshAll_invokesProcessorForEachTrackedSymbol() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.STOCK))
                .thenReturn(List.of("AKBNK.IS", "THYAO.IS"));
        when(snapshotProcessor.updateOne(any())).thenReturn(5);

        service.refreshAll();

        verify(snapshotProcessor).updateOne("AKBNK.IS");
        verify(snapshotProcessor).updateOne("THYAO.IS");
    }

    @Test
    void refresh_normalizesCodeAndDelegatesToProcessor() {
        service.refresh("akbnk");

        verify(snapshotProcessor).updateOne("AKBNK");
    }

    @Test
    void refresh_skipsWhenCodeIsBlank() {
        service.refresh("");

        verify(snapshotProcessor, never()).updateOne(any());
    }

    @Test
    void refresh_skipsWhenCodeIsWhitespace() {
        service.refresh("   ");

        verify(snapshotProcessor, never()).updateOne(any());
    }

    @Test
    void exists_delegatesToProcessor() {
        when(snapshotProcessor.exists("AKBNK.IS")).thenReturn(true);

        assertThat(service.exists("AKBNK.IS")).isTrue();
    }
}
