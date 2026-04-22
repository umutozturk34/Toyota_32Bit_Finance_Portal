package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.repository.CommodityCandleRepository;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.SyntheticPriceCalculator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Service
@Log4j2
public class PreciousMetalDerivativeCalculator {

    private static final String GOLD_GRAM_CODE = "GOLD_GRAM";
    private static final String GOLD_YARIM_CODE = "GOLD_YARIM";
    private static final String GOLD_TAM_CODE = "GOLD_TAM";
    private static final String GOLD_CEYREK_CODE = "GOLD_CEYREK";
    private static final String GOLD_CUMHURIYET_CODE = "GOLD_CUMHURIYET";
    private static final String SILVER_GRAM_CODE = "SILVER_GRAM";

    private final CommodityRepository commodityRepository;
    private final CommodityCandleRepository commodityCandleRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final int scale;
    private final BigDecimal spreadRate;
    private final String goldSourceCode;
    private final String silverSourceCode;
    private final Set<String> sourcesWithDerivatives;
    private final BigDecimal gramDivisor;
    private final BigDecimal tamMultiplier;
    private final BigDecimal yarimDivisor;
    private final BigDecimal ceyrekDivisor;
    private final BigDecimal cumhuriyetMultiplier;

    public PreciousMetalDerivativeCalculator(CommodityRepository commodityRepository,
                                             CommodityCandleRepository commodityCandleRepository,
                                             MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                             AppProperties appProperties) {
        AppProperties.Commodity props = appProperties.getCommodity();
        this.commodityRepository = commodityRepository;
        this.commodityCandleRepository = commodityCandleRepository;
        this.commodityCacheService = commodityCacheService;
        this.scale = appProperties.getScale();
        this.spreadRate = props.getSpreadRate();
        this.goldSourceCode = props.getGoldSourceCode();
        this.silverSourceCode = props.getSilverSourceCode();
        this.sourcesWithDerivatives = Set.of(goldSourceCode, silverSourceCode);
        this.gramDivisor = props.getGoldGramDivisor();
        this.tamMultiplier = props.getGoldTamMultiplier();
        this.yarimDivisor = props.getGoldYarimDivisor();
        this.ceyrekDivisor = props.getGoldCeyrekDivisor();
        this.cumhuriyetMultiplier = props.getGoldCumhuriyetMultiplier();
    }

    public boolean hasDerivatives(String commodityCode) {
        return sourcesWithDerivatives.contains(commodityCode);
    }

    public void refreshDerivatives(Commodity source, BigDecimal usdTryRate) {
        if (source == null || source.getCurrentPriceUsd() == null || usdTryRate == null) {
            log.debug("Skipping derivatives: missing source USD price or USDTRY rate for {}",
                    source == null ? "null" : source.getCommodityCode());
            return;
        }
        BigDecimal gramPrice = onsUsdToGramTry(source.getCurrentPriceUsd(), usdTryRate);
        BigDecimal gramPrevious = onsUsdToGramTry(source.getPreviousPriceUsd(), usdTryRate);

        if (goldSourceCode.equals(source.getCommodityCode())) {
            refreshGoldDerivatives(gramPrice, gramPrevious);
        } else if (silverSourceCode.equals(source.getCommodityCode())) {
            persistDerivative(SILVER_GRAM_CODE, gramPrice, gramPrevious);
        }
    }

    public void refreshDerivativeCandles() {
        refreshFromSource(goldSourceCode, List.of(
                new DerivativeSpec(GOLD_GRAM_CODE, this::onsTryToGram),
                new DerivativeSpec(GOLD_TAM_CODE, this::onsTryToTam),
                new DerivativeSpec(GOLD_YARIM_CODE, this::onsTryToYarim),
                new DerivativeSpec(GOLD_CEYREK_CODE, this::onsTryToCeyrek),
                new DerivativeSpec(GOLD_CUMHURIYET_CODE, this::onsTryToCumhuriyet)
        ));
        refreshFromSource(silverSourceCode, List.of(
                new DerivativeSpec(SILVER_GRAM_CODE, this::onsTryToGram)
        ));
    }

    private void refreshFromSource(String sourceCode, List<DerivativeSpec> derivatives) {
        List<CommodityCandle> sourceCandles = commodityCandleRepository
                .findByCommodityCodeOrderByCandleDateAsc(sourceCode);
        if (sourceCandles.isEmpty()) {
            log.debug("No source candles for {} — skipping derivative candle refresh", sourceCode);
            return;
        }
        for (DerivativeSpec spec : derivatives) {
            regenerateDerivativeCandles(spec.code(), sourceCandles, spec.transform());
        }
    }

    @Transactional
    public void regenerateDerivativeCandles(String derivativeCode, List<CommodityCandle> sourceCandles,
                                            UnaryOperator<BigDecimal> transform) {
        Commodity derivative = commodityRepository.findById(derivativeCode).orElse(null);
        if (derivative == null) {
            log.debug("Derivative commodity {} not found — run snapshot first", derivativeCode);
            return;
        }

        Map<LocalDateTime, CommodityCandle> existingByDate = commodityCandleRepository
                .findByCommodityCodeOrderByCandleDateAsc(derivativeCode).stream()
                .collect(Collectors.toMap(CommodityCandle::getCandleDate, Function.identity(), (a, b) -> a));

        List<CommodityCandle> toInsert = new ArrayList<>();
        int updated = 0;
        for (CommodityCandle src : sourceCandles) {
            CommodityCandle existing = existingByDate.get(src.getCandleDate());
            if (existing != null) {
                applyTransform(existing, src, transform);
                updated++;
            } else {
                CommodityCandle fresh = CommodityCandle.builder()
                        .commodity(derivative)
                        .commodityCode(derivativeCode)
                        .candleDate(src.getCandleDate())
                        .open(transform.apply(src.getOpen()))
                        .high(transform.apply(src.getHigh()))
                        .low(transform.apply(src.getLow()))
                        .close(transform.apply(src.getClose()))
                        .build();
                fresh.scaleAndNormalizeOhlc(scale);
                toInsert.add(fresh);
            }
        }
        if (!toInsert.isEmpty()) {
            commodityCandleRepository.saveAll(toInsert);
        }
        commodityCacheService.refreshHistory(derivativeCode);
        log.info("Derivative candles refreshed for {}: {} inserted, {} updated", derivativeCode, toInsert.size(), updated);
    }

    private void applyTransform(CommodityCandle target, CommodityCandle source, UnaryOperator<BigDecimal> transform) {
        target.setOpen(transform.apply(source.getOpen()));
        target.setHigh(transform.apply(source.getHigh()));
        target.setLow(transform.apply(source.getLow()));
        target.setClose(transform.apply(source.getClose()));
        target.scaleAndNormalizeOhlc(scale);
    }

    private BigDecimal onsTryToGram(BigDecimal onsTry) {
        return divide(onsTry, gramDivisor);
    }

    private BigDecimal onsTryToTam(BigDecimal onsTry) {
        return multiply(onsTryToGram(onsTry), tamMultiplier);
    }

    private BigDecimal onsTryToYarim(BigDecimal onsTry) {
        return divide(onsTryToTam(onsTry), yarimDivisor);
    }

    private BigDecimal onsTryToCeyrek(BigDecimal onsTry) {
        return divide(onsTryToTam(onsTry), ceyrekDivisor);
    }

    private BigDecimal onsTryToCumhuriyet(BigDecimal onsTry) {
        return multiply(onsTryToGram(onsTry), cumhuriyetMultiplier);
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
        Commodity derivative = commodityRepository.findById(code)
                .orElseGet(() -> Commodity.builder().commodityCode(code).build());
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

    private record DerivativeSpec(String code, UnaryOperator<BigDecimal> transform) {}
}
