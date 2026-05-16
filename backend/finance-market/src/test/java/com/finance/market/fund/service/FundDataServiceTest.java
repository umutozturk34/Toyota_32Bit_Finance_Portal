package com.finance.market.fund.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.fund.model.Fund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundDataServiceTest {

    @Mock private FundUpdateService updateService;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Fund> cacheService = org.mockito.Mockito.mock(MarketCacheService.class);

    private FundDataService service;

    @BeforeEach
    void setUp() {
        service = new FundDataService(cacheService, updateService);
    }

    @Test
    void should_returnFundType_when_getAssetTypeCalled() {
        assertThat(service.getAssetType()).isEqualTo(TrackedAssetType.FUND);
    }

    @Test
    void should_passValidation_when_fundExists() {
        TrackedAssetUpsertCommand cmd = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.FUND).assetCode("AAK").build();
        when(updateService.exists("AAK")).thenReturn(true);

        assertThatCode(() -> service.validateExists(cmd)).doesNotThrowAnyException();
    }

    @Test
    void should_throwBusinessException_when_fundMissing() {
        TrackedAssetUpsertCommand cmd = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.FUND).assetCode("ZZZ").build();
        when(updateService.exists("ZZZ")).thenReturn(false);

        assertThatThrownBy(() -> service.validateExists(cmd))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void should_delegateRefreshToUpdateService_when_refreshCalled() {
        service.refresh("AAK");

        verify(updateService).refresh("AAK");
    }

    @Test
    void should_delegateRefreshAllToUpdateService_when_refreshAllCalled() {
        service.refreshAll();

        verify(updateService).refreshAll();
    }

    @Test
    void should_delegateClearCacheToHelper_when_clearCacheCalled() {
        service.clearCache("AAK");

        verify(cacheService).clearCache("AAK");
    }
}
