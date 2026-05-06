package com.finance.commodity.service;
import com.finance.cache.service.MarketCacheService;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.common.config.AppProperties;
import com.finance.commodity.config.CommodityProperties;
import com.finance.commodity.config.CommodityProperties.DerivativeRule;
import com.finance.commodity.model.Commodity;
import com.finance.commodity.model.CommodityCandle;
import com.finance.commodity.repository.CommodityCandleRepository;
import com.finance.commodity.repository.CommodityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreciousMetalDerivativeCalculatorTest {

    @SuppressWarnings("unchecked")
    private final MarketCacheService<Commodity> cacheService = mock(MarketCacheService.class);
    private final CommodityRepository repository = mock(CommodityRepository.class);
    private final CommodityCandleRepository candleRepository = mock(CommodityCandleRepository.class);

    private Map<String, Commodity> derivativeStore;
    private PreciousMetalDerivativeCalculator calculator;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setScale(4);
        CommodityProperties commodityProps = new CommodityProperties();
        commodityProps.setDerivatives(List.of(
                new DerivativeRule("GC=F", "XAUTRYG", new BigDecimal("31.1035"), "Altın (Gram)", "Gram Altın"),
                new DerivativeRule("SI=F", "XAGTRYG", new BigDecimal("31.1035"), "Gümüş (Gram)", "Gram Gümüş")
        ));

        derivativeStore = new HashMap<>();
        seedDerivative("XAUTRYG");
        seedDerivative("XAGTRYG");

        when(repository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(derivativeStore.get((String) inv.getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        calculator = new PreciousMetalDerivativeCalculator(repository, candleRepository, cacheService,
                new CommoditySegmentResolver(commodityProps), props, commodityProps);
    }

    @Test
    void hasDerivativesReturnsTrueForGoldAndSilver() {
        assertThat(calculator.hasDerivatives("GC=F")).isTrue();
        assertThat(calculator.hasDerivatives("SI=F")).isTrue();
    }

    @Test
    void hasDerivativesReturnsFalseForOtherCommodities() {
        assertThat(calculator.hasDerivatives("PLATINUM")).isFalse();
        assertThat(calculator.hasDerivatives("BRENT")).isFalse();
        assertThat(calculator.hasDerivatives("WHEAT")).isFalse();
    }

    @Test
    void refreshDerivativesComputesGoldGramForGoldSource() {
        Commodity gold = buildCommodity("GC=F", new BigDecimal("4000"));
        BigDecimal usdTry = new BigDecimal("40.0");

        calculator.refreshDerivatives(gold, usdTry, usdTry);

        verify(cacheService).putSnapshot(eq("XAUTRYG"), any(Commodity.class));
        verify(cacheService, never()).putSnapshot(eq("XAGTRYG"), any(Commodity.class));
    }

    @Test
    void refreshDerivativesComputesSilverGramForSilverSource() {
        Commodity silver = buildCommodity("SI=F", new BigDecimal("50"));
        BigDecimal usdTry = new BigDecimal("40.0");

        calculator.refreshDerivatives(silver, usdTry, usdTry);

        verify(cacheService).putSnapshot(eq("XAGTRYG"), any(Commodity.class));
        verify(cacheService, never()).putSnapshot(eq("XAUTRYG"), any(Commodity.class));
    }

    @Test
    void refreshDerivativesSkipsWhenUsdPriceMissing() {
        Commodity gold = new Commodity();
        gold.setCommodityCode("GC=F");
        gold.setCurrentPriceUsd(null);

        calculator.refreshDerivatives(gold, new BigDecimal("40"), new BigDecimal("40"));

        verify(repository, never()).save(any());
    }

    @Test
    void refreshDerivativesSkipsWhenUsdTryRateMissing() {
        Commodity gold = buildCommodity("GC=F", new BigDecimal("4000"));

        calculator.refreshDerivatives(gold, null, null);

        verify(repository, never()).save(any());
    }

    @Test
    void refreshDerivativesAppliesCorrectGoldGramMath() {
        Commodity gold = buildCommodity("GC=F", new BigDecimal("4000"));
        BigDecimal usdTry = new BigDecimal("40.0");

        calculator.refreshDerivatives(gold, usdTry, usdTry);

        ArgumentCaptor<Commodity> captor = ArgumentCaptor.forClass(Commodity.class);
        verify(cacheService, atLeastOnce()).putSnapshot(eq("XAUTRYG"), captor.capture());
        BigDecimal expectedGram = new BigDecimal("4000").multiply(usdTry)
                .divide(new BigDecimal("31.1035"), 4, java.math.RoundingMode.HALF_UP);
        assertThat(captor.getValue().getCurrentPrice()).isEqualByComparingTo(expectedGram);
    }

    private Commodity buildCommodity(String code, BigDecimal usdPrice) {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode(code);
        commodity.setCurrentPriceUsd(usdPrice);
        commodity.setPreviousPriceUsd(usdPrice.multiply(new BigDecimal("0.99")));
        commodity.setCurrentPrice(usdPrice.multiply(new BigDecimal("40.0")));
        return commodity;
    }

    private void seedDerivative(String code) {
        Commodity c = new Commodity();
        c.setCommodityCode(code);
        derivativeStore.put(code, c);
    }
}
