package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.repository.CommodityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
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
    private final MarketCacheService<Commodity, CommodityCandle> cacheService = mock(MarketCacheService.class);
    private final CommodityRepository repository = mock(CommodityRepository.class);

    private Map<String, Commodity> derivativeStore;
    private PreciousMetalDerivativeCalculator calculator;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        AppProperties.Commodity commodityProps = new AppProperties.Commodity();
        commodityProps.setSpreadRate(new BigDecimal("0.015"));
        commodityProps.setGoldGramDivisor(new BigDecimal("31.1035"));
        commodityProps.setGoldTamMultiplier(new BigDecimal("7.02"));
        commodityProps.setGoldYarimDivisor(new BigDecimal("2.0"));
        commodityProps.setGoldCeyrekDivisor(new BigDecimal("4.0"));
        commodityProps.setGoldCumhuriyetMultiplier(new BigDecimal("7.216"));
        props.setCommodity(commodityProps);
        props.setScale(4);

        derivativeStore = new HashMap<>();
        seedDerivative("GOLD_GRAM");
        seedDerivative("GOLD_TAM");
        seedDerivative("GOLD_YARIM");
        seedDerivative("GOLD_CEYREK");
        seedDerivative("GOLD_CUMHURIYET");
        seedDerivative("SILVER_GRAM");

        when(repository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(derivativeStore.get((String) inv.getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        calculator = new PreciousMetalDerivativeCalculator(repository, cacheService, props);
    }

    @Test
    void hasDerivativesReturnsTrueForGoldAndSilver() {
        assertThat(calculator.hasDerivatives("GOLD")).isTrue();
        assertThat(calculator.hasDerivatives("SILVER")).isTrue();
    }

    @Test
    void hasDerivativesReturnsFalseForOtherCommodities() {
        assertThat(calculator.hasDerivatives("PLATINUM")).isFalse();
        assertThat(calculator.hasDerivatives("BRENT")).isFalse();
        assertThat(calculator.hasDerivatives("WHEAT")).isFalse();
    }

    @Test
    void refreshDerivativesComputesFiveGoldVariantsForGoldSource() {
        Commodity gold = buildCommodity("GOLD", new BigDecimal("4000"));
        BigDecimal usdTry = new BigDecimal("40.0");

        calculator.refreshDerivatives(gold, usdTry);

        verify(cacheService).putSnapshot(eq("GOLD_GRAM"), any(Commodity.class));
        verify(cacheService).putSnapshot(eq("GOLD_TAM"), any(Commodity.class));
        verify(cacheService).putSnapshot(eq("GOLD_YARIM"), any(Commodity.class));
        verify(cacheService).putSnapshot(eq("GOLD_CEYREK"), any(Commodity.class));
        verify(cacheService).putSnapshot(eq("GOLD_CUMHURIYET"), any(Commodity.class));
    }

    @Test
    void refreshDerivativesComputesSingleGramForSilverSource() {
        Commodity silver = buildCommodity("SILVER", new BigDecimal("50"));
        BigDecimal usdTry = new BigDecimal("40.0");

        calculator.refreshDerivatives(silver, usdTry);

        verify(cacheService).putSnapshot(eq("SILVER_GRAM"), any(Commodity.class));
        verify(cacheService, never()).putSnapshot(eq("GOLD_GRAM"), any(Commodity.class));
    }

    @Test
    void refreshDerivativesSkipsWhenUsdPriceMissing() {
        Commodity gold = new Commodity();
        gold.setCommodityCode("GOLD");
        gold.setCurrentPriceUsd(null);

        calculator.refreshDerivatives(gold, new BigDecimal("40"));

        verify(repository, never()).save(any());
    }

    @Test
    void refreshDerivativesSkipsWhenUsdTryRateMissing() {
        Commodity gold = buildCommodity("GOLD", new BigDecimal("4000"));

        calculator.refreshDerivatives(gold, null);

        verify(repository, never()).save(any());
    }

    @Test
    void refreshDerivativesAppliesCorrectGoldGramMath() {
        Commodity gold = buildCommodity("GOLD", new BigDecimal("4000"));
        BigDecimal usdTry = new BigDecimal("40.0");

        calculator.refreshDerivatives(gold, usdTry);

        ArgumentCaptor<Commodity> captor = ArgumentCaptor.forClass(Commodity.class);
        verify(cacheService, atLeastOnce()).putSnapshot(eq("GOLD_GRAM"), captor.capture());
        BigDecimal expectedGram = new BigDecimal("4000").multiply(usdTry)
                .divide(new BigDecimal("31.1035"), 4, java.math.RoundingMode.HALF_UP);
        assertThat(captor.getValue().getCurrentPrice()).isEqualByComparingTo(expectedGram);
    }

    private Commodity buildCommodity(String code, BigDecimal usdPrice) {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode(code);
        commodity.setCurrentPriceUsd(usdPrice);
        commodity.setPreviousPriceUsd(usdPrice.multiply(new BigDecimal("0.99")));
        return commodity;
    }

    private void seedDerivative(String code) {
        Commodity c = new Commodity();
        c.setCommodityCode(code);
        derivativeStore.put(code, c);
    }
}
