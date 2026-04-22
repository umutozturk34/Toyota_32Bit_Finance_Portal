package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.CommoditySegment;
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
    }

    public boolean hasDerivatives(String commodityCode) {
        return sourcesWithDerivatives.contains(commodityCode);
    }

    public void refreshDerivatives(Commodity source, BigDecimal usdTryCurrent, BigDecimal usdTryPrevious) {
        if (source == null || source.getCurrentPriceUsd() == null || usdTryCurrent == null) {
            log.debug("Skipping derivatives: missing source USD price or USDTRY rate for {}",
                    source == null ? "null" : source.getCommodityCode());
            return;
        }
        BigDecimal gramCurrent = divide(source.getCurrentPrice(), gramDivisor);
        BigDecimal gramOpen = divide(source.getOpenPrice(), gramDivisor);
        BigDecimal gramHigh = divide(source.getDayHigh(), gramDivisor);
        BigDecimal gramLow = divide(source.getDayLow(), gramDivisor);
        BigDecimal gramPrevious = onsUsdToGramTry(source.getPreviousPriceUsd(),
                usdTryPrevious != null ? usdTryPrevious : usdTryCurrent);
        Long volume = source.getVolume();

        if (goldSourceCode.equals(source.getCommodityCode())) {
            persistDerivative(GOLD_GRAM_CODE, gramCurrent, gramPrevious, gramOpen, gramHigh, gramLow, volume);
        } else if (silverSourceCode.equals(source.getCommodityCode())) {
            persistDerivative(SILVER_GRAM_CODE, gramCurrent, gramPrevious, gramOpen, gramHigh, gramLow, volume);
        }
    }

    public void refreshDerivativeCandles() {
        refreshFromSource(goldSourceCode, GOLD_GRAM_CODE);
        refreshFromSource(silverSourceCode, SILVER_GRAM_CODE);
    }

    private void refreshFromSource(String sourceCode, String derivativeCode) {
        List<CommodityCandle> sourceCandles = commodityCandleRepository
                .findByCommodityCodeOrderByCandleDateAsc(sourceCode);
        if (sourceCandles.isEmpty()) {
            log.debug("No source candles for {} — skipping derivative candle refresh", sourceCode);
            return;
        }
        regenerateDerivativeCandles(derivativeCode, sourceCandles, this::onsTryToGram);
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

    private BigDecimal onsUsdToGramTry(BigDecimal onsUsd, BigDecimal usdTryRate) {
        if (onsUsd == null) return null;
        BigDecimal onsTry = onsUsd.multiply(usdTryRate);
        return divide(onsTry, gramDivisor);
    }

    private void persistDerivative(String code, BigDecimal tryPrice, BigDecimal tryPrevious,
                                   BigDecimal tryOpen, BigDecimal tryHigh, BigDecimal tryLow, Long volume) {
        if (tryPrice == null) return;
        Commodity derivative = commodityRepository.findById(code)
                .orElseGet(() -> Commodity.builder()
                        .commodityCode(code)
                        .commoditySegment(CommoditySegment.fromCode(code))
                        .build());
        derivative.applyDerivedSnapshot(tryPrice, tryPrevious, tryOpen, tryHigh, tryLow, volume, spreadRate, scale);
        commodityRepository.save(derivative);
        commodityCacheService.putSnapshot(code, derivative);
    }

    private BigDecimal divide(BigDecimal value, BigDecimal divisor) {
        return SyntheticPriceCalculator.safeDivide(value, divisor, scale);
    }
}
