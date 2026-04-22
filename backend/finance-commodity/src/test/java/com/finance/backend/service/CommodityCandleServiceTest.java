package com.finance.backend.service;

import com.finance.backend.client.YahooCommodityClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.mapper.CommodityMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityCandleRepository;
import com.finance.backend.repository.CommodityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommodityCandleServiceTest {

    private YahooCommodityClient yahooCommodityClient;
    private CommodityMapper commodityMapper;
    private CommodityRepository commodityRepository;
    private CommodityCandleRepository commodityCandleRepository;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService = mock(MarketCacheService.class);
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Forex, ForexCandle> forexCacheService = mock(MarketCacheService.class);
    private TrackedAssetQueryService trackedAssetQueryService;
    private CommodityCandleService service;

    @BeforeEach
    void setUp() {
        yahooCommodityClient = mock(YahooCommodityClient.class);
        commodityMapper = mock(CommodityMapper.class);
        commodityRepository = mock(CommodityRepository.class);
        commodityCandleRepository = mock(CommodityCandleRepository.class);
        trackedAssetQueryService = mock(TrackedAssetQueryService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        AppProperties props = new AppProperties();
        AppProperties.Commodity commodityProps = new AppProperties.Commodity();
        commodityProps.setYearsToKeep(5);
        props.setCommodity(commodityProps);
        props.setScale(4);
        props.setTimezone("Europe/Istanbul");

        when(commodityCandleRepository.deleteByCandleDateBefore(any())).thenReturn(0);

        PreciousMetalDerivativeCalculator derivativeCalculator = mock(PreciousMetalDerivativeCalculator.class);
        service = new CommodityCandleService(
                yahooCommodityClient,
                commodityMapper,
                commodityRepository,
                commodityCandleRepository,
                commodityCacheService,
                forexCacheService,
                trackedAssetQueryService,
                derivativeCalculator,
                transactionManager,
                props);
    }

    @Test
    void getMarketTypeReturnsCommodity() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.COMMODITY);
    }

    @Test
    void refreshAllSkipsWhenNoTrackedCodes() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.COMMODITY))
                .thenReturn(Collections.emptyList());

        service.refreshAll();

        verify(yahooCommodityClient, never()).fetchCandles(anyString(), anyString(), anyString(), any(Boolean.class));
        verify(forexCacheService, never()).getHistory(anyString());
    }

    @Test
    void refreshAllSkipsWhenAllCodesAreDerivatives() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.COMMODITY))
                .thenReturn(List.of("GOLD_GRAM", "SILVER_GRAM", "GOLD_TAM"));

        service.refreshAll();

        verify(yahooCommodityClient, never()).fetchCandles(anyString(), anyString(), anyString(), any(Boolean.class));
        verify(forexCacheService, never()).getHistory(anyString());
    }

    @Test
    void refreshAllSkipsWhenUsdTryCandlesUnavailable() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.COMMODITY))
                .thenReturn(List.of("GC=F", "SI=F", "GOLD_GRAM"));
        when(forexCacheService.getHistory("USDTRY")).thenReturn(Collections.emptyList());

        service.refreshAll();

        verify(yahooCommodityClient, never()).fetchCandles(anyString(), anyString(), anyString(), any(Boolean.class));
    }

    @Test
    void refreshAllFiltersDerivativesFromYahooFetch() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.COMMODITY))
                .thenReturn(List.of("GC=F", "GOLD_GRAM", "SI=F", "SILVER_GRAM"));
        when(forexCacheService.getHistory("USDTRY")).thenReturn(Collections.emptyList());

        service.refreshAll();

        verify(yahooCommodityClient, never()).fetchCandles(eq("GOLD_GRAM"), anyString(), anyString(), any(Boolean.class));
        verify(yahooCommodityClient, never()).fetchCandles(eq("SILVER_GRAM"), anyString(), anyString(), any(Boolean.class));
    }
}
