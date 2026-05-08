package com.finance.market.forex.service;
import com.finance.common.service.MarketEntityWriter;


import com.finance.common.dto.external.YahooCandleDto;
import com.finance.common.dto.external.YahooQuoteDto;
import com.finance.market.forex.mapper.ForexMapper;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.common.util.CandleBatchUpsertTemplate;
import com.finance.common.util.ChangeFromCandlesUpdater;
import com.finance.common.util.SyntheticPriceCalculator;
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
                               boolean isUsdBase, BigDecimal spreadRate, int scale,
                               YahooCandleDto todayTryCandle) {
        BigDecimal previousClose = SyntheticPriceCalculator.calculateSyntheticPreviousClose(
                pairQuote.previousClose(), usdtry.getCurrentPrice(), usdtry.getChangeAmount(), isUsdBase, scale);
        forex.applySyntheticPrice(todayTryCandle.close(), previousClose,
                todayTryCandle.open(), todayTryCandle.high(), todayTryCandle.low(),
                spreadRate, scale);
        forexRepository.save(forex);
    }

    public boolean refreshChangePercentFromCandles(Forex forex, int scale) {
        List<ForexCandle> top2 = forexCandleRepository
                .findTop2ByCurrencyCodeOrderByCandleDateDesc(forex.getCurrencyCode());
        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                forex, forex.getCurrentPrice(), top2, scale);
        if (changed) forexRepository.save(forex);
        return changed;
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
