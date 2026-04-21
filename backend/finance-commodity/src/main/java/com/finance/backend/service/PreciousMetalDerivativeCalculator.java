package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.SyntheticPriceCalculator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

@Service
@Log4j2
public class PreciousMetalDerivativeCalculator {

    private static final String GOLD_CODE = "GOLD";
    private static final String SILVER_CODE = "SILVER";
    private static final Set<String> SOURCES_WITH_DERIVATIVES = Set.of(GOLD_CODE, SILVER_CODE);

    private static final String GOLD_GRAM_CODE = "GOLD_GRAM";
    private static final String GOLD_YARIM_CODE = "GOLD_YARIM";
    private static final String GOLD_TAM_CODE = "GOLD_TAM";
    private static final String GOLD_CEYREK_CODE = "GOLD_CEYREK";
    private static final String GOLD_CUMHURIYET_CODE = "GOLD_CUMHURIYET";
    private static final String SILVER_GRAM_CODE = "SILVER_GRAM";

    private final CommodityRepository commodityRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final int scale;
    private final BigDecimal spreadRate;
    private final BigDecimal gramDivisor;
    private final BigDecimal tamMultiplier;
    private final BigDecimal yarimDivisor;
    private final BigDecimal ceyrekDivisor;
    private final BigDecimal cumhuriyetMultiplier;

    public PreciousMetalDerivativeCalculator(CommodityRepository commodityRepository,
                                             MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
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

    public boolean hasDerivatives(String commodityCode) {
        return SOURCES_WITH_DERIVATIVES.contains(commodityCode);
    }

    public void refreshDerivatives(Commodity source, BigDecimal usdTryRate) {
        if (source == null || source.getCurrentPriceUsd() == null || usdTryRate == null) {
            log.debug("Skipping derivatives: missing source USD price or USDTRY rate for {}",
                    source == null ? "null" : source.getCommodityCode());
            return;
        }
        BigDecimal gramPrice = onsUsdToGramTry(source.getCurrentPriceUsd(), usdTryRate);
        BigDecimal gramPrevious = onsUsdToGramTry(source.getPreviousPriceUsd(), usdTryRate);

        if (GOLD_CODE.equals(source.getCommodityCode())) {
            refreshGoldDerivatives(gramPrice, gramPrevious);
        } else if (SILVER_CODE.equals(source.getCommodityCode())) {
            persistDerivative(SILVER_GRAM_CODE, gramPrice, gramPrevious);
        }
    }

    private void refreshGoldDerivatives(BigDecimal gramPrice, BigDecimal gramPrevious) {
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

    private BigDecimal onsUsdToGramTry(BigDecimal onsUsd, BigDecimal usdTryRate) {
        if (onsUsd == null) return null;
        BigDecimal onsTry = onsUsd.multiply(usdTryRate);
        return divide(onsTry, gramDivisor);
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
