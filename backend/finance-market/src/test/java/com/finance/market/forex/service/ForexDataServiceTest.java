package com.finance.market.forex.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.forex.model.Forex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexDataServiceTest {

    @Mock private ForexUpdateService forexUpdateService;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Forex> cacheService = org.mockito.Mockito.mock(MarketCacheService.class);

    private ForexDataService service;

    @BeforeEach
    void setUp() {
        service = new ForexDataService(cacheService, forexUpdateService);
    }

    @Test
    void should_returnForexType_when_getAssetTypeCalled() {
        TrackedAssetType type = service.getAssetType();

        org.assertj.core.api.Assertions.assertThat(type).isEqualTo(TrackedAssetType.FOREX);
    }

    @Test
    void should_pass_when_validateExistsWithActiveCurrency() {
        TrackedAssetUpsertCommand command = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.FOREX).assetCode("USD").build();
        when(forexUpdateService.isActiveCurrency("USD")).thenReturn(true);

        assertThatCode(() -> service.validateExists(command)).doesNotThrowAnyException();
    }

    @Test
    void should_throwBusinessException_when_validateExistsWithInactiveCurrency() {
        TrackedAssetUpsertCommand command = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.FOREX).assetCode("ZZZ").build();
        when(forexUpdateService.isActiveCurrency("ZZZ")).thenReturn(false);

        assertThatThrownBy(() -> service.validateExists(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.market.forexNotFound");
    }

    @Test
    void should_delegateToUpdateService_when_refreshCalled() {
        service.refresh("USD");

        verify(forexUpdateService).refresh("USD");
    }

    @Test
    void should_delegateToUpdateService_when_refreshAllCalled() {
        service.refreshAll();

        verify(forexUpdateService).refreshAll();
    }

    @Test
    void should_delegateToCacheHelper_when_clearCacheCalled() {
        service.clearCache("USD");

        verify(cacheService).clearCache("USD");
    }
}
