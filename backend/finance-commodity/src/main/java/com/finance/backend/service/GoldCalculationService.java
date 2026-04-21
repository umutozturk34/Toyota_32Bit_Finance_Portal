package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.Commodity;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.SyntheticPriceCalculator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Log4j2
public class GoldCalculationService {

    private static final String GOLD_CODE = "GOLD";
    private static final String GOLD_GRAM_CODE = "GOLD_GRAM";
    private static final String GOLD_YARIM_CODE = "GOLD_YARIM";
    private static final String GOLD_TAM_CODE = "GOLD_TAM";
    private static final String GOLD_CEYREK_CODE = "GOLD_CEYREK";
    private static final String GOLD_CUMHURIYET_CODE = "GOLD_CUMHURIYET";

    private final CommodityRepository commodityRepository;
    private final MarketCacheService<Commodity, com.finance.backend.model.CommodityCandle> commodityCacheService;
    private final int scale;
    private final BigDecimal spreadRate;
    private final BigDecimal gramDivisor;
    private final BigDecimal tamMultiplier;
    private final BigDecimal yarimDivisor;
    private final BigDecimal ceyrekDivisor;
    private final BigDecimal cumhuriyetMultiplier;

    public GoldCalculationService(CommodityRepository commodityRepository,
                                  MarketCacheService<Commodity, com.finance.backend.model.CommodityCandle> commodityCacheService,
                                  AppProperties appProperties) {
        this.commodityRepository = commodityRepository;
        this.commodityCacheService = commodityCacheService;
        this.scale = appProperties.getScale();
        this.spreadRate = appProperties.getCommodity().getSpreadRate();
        this.gramDivisor = appProperties.getCommodity().getGoldGramDivisor();
        this.tamMultiplier = appProperties.getCommodity().getGoldTamMultiplier();
        this.yarimDivisor = appProperties.getCommodity().getGoldYarimDivisor();
        this.ceyrekDivisor = appProperties.getCommodity().getGoldCeyrekDivisor();
        this.cumhuriyetMultiplier = appProperties.getCommodity().getGoldCumhuriyetMultiplier();
    }

    public void refreshDerivatives(Commodity gold, BigDecimal usdTryRate) {
        if (gold == null || gold.getCurrentPriceUsd() == null || usdTryRate == null) {
            log.debug("Skipping gold derivatives: missing gold USD price or USDTRY rate");
            return;
        }
        BigDecimal onsTry = gold.getCurrentPriceUsd().multiply(usdTryRate);
        BigDecimal onsTryPrevious = gold.getPreviousPriceUsd() != null
                ? gold.getPreviousPriceUsd().multiply(usdTryRate)
                : null;

        BigDecimal gramPrice = divide(onsTry, gramDivisor);
        BigDecimal gramPrevious = divide(onsTryPrevious, gramDivisor);
        BigDecimal tamPrice = multiply(gramPrice, tamMultiplier);
        BigDecimal tamPrevious = multiply(gramPrevious, tamMultiplier);
        BigDecimal yarimPrice = divide(tamPrice, yarimDivisor);
        BigDecimal yarimPrevious = divide(tamPrevious, yarimDivisor);
        BigDecimal ceyrekPrice = divide(tamPrice, ceyrekDivisor);
        BigDecimal ceyrekPrevious = divide(tamPrevious, ceyrekDivisor);
        BigDecimal cumhuriyetPrice = multiply(gramPrice, cumhuriyetMultiplier);
        BigDecimal cumhuriyetPrevious = multiply(gramPrevious, cumhuriyetMultiplier);

        persistDerivative(GOLD_GRAM_CODE, gramPrice, gramPrevious);
        persistDerivative(GOLD_TAM_CODE, tamPrice, tamPrevious);
        persistDerivative(GOLD_YARIM_CODE, yarimPrice, yarimPrevious);
        persistDerivative(GOLD_CEYREK_CODE, ceyrekPrice, ceyrekPrevious);
        persistDerivative(GOLD_CUMHURIYET_CODE, cumhuriyetPrice, cumhuriyetPrevious);
    }

    public boolean isGoldSource(String commodityCode) {
        return GOLD_CODE.equals(commodityCode);
    }

    private void persistDerivative(String code, BigDecimal tryPrice, BigDecimal tryPrevious) {
        if (tryPrice == null) return;
        Optional<Commodity> maybe = commodityRepository.findById(code);
        if (maybe.isEmpty()) {
            log.warn("Derivative commodity {} not found in DB, skipping", code);
            return;
        }
        Commodity derivative = maybe.get();
        derivative.applyDerivedSnapshot(tryPrice, tryPrevious, spreadRate, scale);
        commodityRepository.save(derivative);
        commodityCacheService.putSnapshot(code, derivative);
    }

    private BigDecimal divide(BigDecimal value, BigDecimal divisor) {
        return SyntheticPriceCalculator.safeDivide(value, divisor, scale);
    }

    private BigDecimal multiply(BigDecimal value, BigDecimal multiplier) {
        return SyntheticPriceCalculator.safeMultiply(value, multiplier, scale);
    }
}
