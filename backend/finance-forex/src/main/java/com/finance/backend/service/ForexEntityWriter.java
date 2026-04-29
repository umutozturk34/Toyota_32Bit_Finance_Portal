package com.finance.backend.service;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.util.CandleBatchUpsertTemplate;
import com.finance.backend.util.SyntheticPriceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class ForexEntityWriter implements MarketEntityWriter {

    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final ForexMapper forexMapper;

    public void applyDirect(Forex forex, YahooQuoteDto quote, BigDecimal spreadRate, int scale) {
        forex.applyYahooSnapshot(
                quote.regularMarketPrice(), quote.previousClose(),
                quote.openPrice(), quote.dayHigh(), quote.dayLow(), quote.volume(),
                spreadRate, scale);
        forexRepository.save(forex);
    }

    public void applySynthetic(Forex forex, YahooQuoteDto pairQuote, Forex usdtry,
                               boolean isUsdBase, BigDecimal spreadRate, int scale) {
        BigDecimal syntheticPrice = SyntheticPriceCalculator.calculateSyntheticPrice(
                pairQuote.regularMarketPrice(), usdtry.getCurrentPrice(), isUsdBase, scale);
        if (syntheticPrice == null) return;
        BigDecimal syntheticPreviousClose = SyntheticPriceCalculator.calculateSyntheticPreviousClose(
                pairQuote.previousClose(), usdtry.getCurrentPrice(), usdtry.getChangeAmount(), isUsdBase, scale);
        forex.applySyntheticPrice(syntheticPrice, syntheticPreviousClose, spreadRate, scale);
        forexRepository.save(forex);
    }

    public int upsertCandles(Forex forex, List<YahooCandleDto> candleDtos) {
        List<YahooCandleDto> uniqueDtos = candleDtos.stream()
                .collect(Collectors.toMap(
                        dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                        dto -> dto, (a, b) -> b, LinkedHashMap::new))
                .values().stream().toList();
        CandleBatchUpsertTemplate.UpsertResult<ForexCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                uniqueDtos,
                dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                keys -> forexCandleRepository.findByCurrencyCodeAndCandleDateIn(forex.getCurrencyCode(), keys),
                candle -> candle.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                forexMapper::updateCandleEntity,
                dto -> forexMapper.toCandleEntity(dto, forex.getCurrencyCode(), forex));
        if (!upsertResult.newEntities().isEmpty()) {
            forexCandleRepository.saveAll(upsertResult.newEntities());
        }
        return upsertResult.totalChanged();
    }
}
