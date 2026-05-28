package com.finance.market.forex.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.core.service.MarketEntityWriter;
import com.finance.market.core.util.CandleBatchUpsertTemplate;
import com.finance.market.forex.config.ForexProperties;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Persists forex entities and candles. Provides a shell-upsert (identity + display/flag metadata),
 * snapshot save (also pushing to cache), and idempotent batch candle upsert that updates changed
 * rows and inserts new ones.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ForexEntityWriter implements MarketEntityWriter {

    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final MarketCacheService<Forex> forexCacheService;
    private final AssetRegistryService assetRegistry;
    private final ForexProperties forexProperties;
    private final AppProperties appProperties;

    /** Creates or updates the forex identity row (name, flag emoji, instrument link) without prices. */
    public Forex upsertForexShell(ForexSerieMetadata meta) {
        Forex forex = forexRepository.findById(meta.currencyCode())
                .orElseGet(() -> Forex.builder().currencyCode(meta.currencyCode()).build());
        forex.setName(meta.displayNameTr());
        forex.setImage(forexProperties.getFlagEmojis().get(meta.currencyCode()));
        forex.setAsset(assetRegistry.upsert(MarketType.FOREX, meta.currencyCode()));
        return forexRepository.save(forex);
    }

    public Forex saveSnapshot(Forex forex) {
        Forex saved = forexRepository.save(forex);
        forexCacheService.putSnapshot(saved.getCurrencyCode(), saved);
        return saved;
    }

    public int upsertCandles(Forex forex, List<ForexCandle> candidates) {
        if (candidates.isEmpty()) return 0;
        String currencyCode = forex.getCurrencyCode();
        CandleBatchUpsertTemplate.UpsertResult<ForexCandle> result = CandleBatchUpsertTemplate.upsert(
                candidates,
                ForexCandle::getCandleDate,
                keys -> forexCandleRepository.findByCurrencyCodeAndCandleDateIn(currencyCode, keys),
                ForexCandle::getCandleDate,
                this::updateExistingCandle,
                candidate -> attachParent(candidate, forex));
        if (!result.newEntities().isEmpty()) {
            forexCandleRepository.saveAll(result.newEntities());
        }
        if (result.totalChanged() > 0) {
            log.debug("{} - {} new candles, {} updated", currencyCode, result.insertCount(), result.updateCount());
        }
        return result.totalChanged();
    }

    private void updateExistingCandle(ForexCandle existing, ForexCandle candidate) {
        existing.setSellingPrice(candidate.getSellingPrice());
        existing.setBuyingPrice(candidate.getBuyingPrice());
        existing.setEffectiveBuyingPrice(candidate.getEffectiveBuyingPrice());
        existing.setEffectiveSellingPrice(candidate.getEffectiveSellingPrice());
    }

    private ForexCandle attachParent(ForexCandle candidate, Forex forex) {
        candidate.setForex(forex);
        candidate.setCurrencyCode(forex.getCurrencyCode());
        return candidate;
    }

    public int getScale() {
        return appProperties.getScale();
    }
}
