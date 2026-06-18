package com.finance.market.fund.service;
import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.cache.MarketCacheService;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.market.fund.client.TefasClient;
import com.finance.common.config.AppProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import com.finance.market.core.util.ApiAssetValidator;
import com.finance.shared.util.CodeNormalizer;
import com.finance.market.fund.util.TefasHelper;
import com.finance.market.core.util.TrackedRefreshRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Refreshes fund snapshots from TEFAS for both BYF and YAT types, auto-tracking newly seen funds.
 * The effective date walks back day-by-day (bounded by a holiday lookback) until data is found,
 * skipping TEFAS holidays. An open circuit breaker aborts the type with a sentinel result.
 */
@Log4j2
@Component
public class FundSnapshotProcessor implements MarketSnapshotProcessor {

    private final TefasClient tefasClient;
    private final FundEntityWriter entityWriter;
    private final MarketCacheService<Fund> fundCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId appZone;
    private final int eodCutoverHour;
    private final int holidayLookbackDays;

    public FundSnapshotProcessor(TefasClient tefasClient,
                                 FundEntityWriter entityWriter,
                                 MarketCacheService<Fund> fundCacheService,
                                 TrackedAssetQueryService trackedAssetQueryService,
                                 TransactionTemplate transactionTemplate,
                                 AppProperties appProperties,
                                 FundProperties fundProperties) {
        this.tefasClient = tefasClient;
        this.entityWriter = entityWriter;
        this.fundCacheService = fundCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.transactionTemplate = transactionTemplate;
        this.appZone = ZoneId.of(appProperties.getTimezone());
        this.eodCutoverHour = fundProperties.getTefasEodCutoverHour();
        this.holidayLookbackDays = fundProperties.getHolidayLookbackDays();
    }

    /** Bulk-refreshes both BYF and YAT snapshots for the latest publishing day, walking back over TEFAS holidays. */
    public void refreshAll() {
        long start = System.currentTimeMillis();
        LocalDate cursor = today();
        log.info("Starting bulk fund snapshot update for {}", cursor);

        int byfSaved = -1;
        int yatSaved = -1;

        for (int attempt = 0; attempt <= holidayLookbackDays; attempt++) {
            byfSaved = bulkUpdateAndAutoTrackBYF(cursor);
            yatSaved = bulkUpdateAndAutoTrackYAT(cursor);
            if (byfSaved > 0 || yatSaved > 0) break;
            if (attempt == holidayLookbackDays) break;
            log.info("No fund data for {}, walking back one day (TEFAS likely closed for holiday)", cursor);
            cursor = cursor.minusDays(1);
        }

        log.info("[TIMING] Fund snapshot update took {}s (effective date {}, BYF saved={}, YAT saved={})",
                (System.currentTimeMillis() - start) / 1000, cursor,
                byfSaved < 0 ? "FAILED" : byfSaved, yatSaved < 0 ? "FAILED" : yatSaved);
    }

    public void refreshOne(String fundCode) {
        TrackedRefreshRunner.refreshSnapshot(fundCode, CodeNormalizer::upper, normalized -> {
            LocalDate today = today();
            for (FundType type : List.of(FundType.YAT, FundType.BYF)) {
                List<TefasFundDto> funds = tefasClient.post(type, normalized, today, today);
                if (funds.isEmpty()) continue;
                TefasFundDto dto = funds.getFirst();
                Fund saved = transactionTemplate.execute(s -> {
                    Fund f = entityWriter.saveSnapshot(dto, type);
                    entityWriter.upsertCandleFromDto(f, type, dto);
                    return f;
                });
                fundCacheService.putSnapshot(saved.getFundCode(), saved);
                return true;
            }
            log.warn("No snapshot data found for tracked fund {}", normalized);
            return false;
        }, log, "fund");
    }

    public boolean exists(String fundCode) {
        return ApiAssetValidator.validate(fundCode, true, code -> {
            LocalDate today = today();
            return !tefasClient.post(FundType.YAT, code, today, today).isEmpty()
                    || !tefasClient.post(FundType.BYF, code, today, today).isEmpty();
        }, log, "Fund");
    }

    private LocalDate today() {
        return TefasHelper.findLastBusinessDay(LocalDate.now(appZone), appZone, eodCutoverHour);
    }

    private int bulkUpdateAndAutoTrackBYF(LocalDate today) {
        return executeBulk(FundType.BYF, today, dto -> true, this::persistByf);
    }

    private int bulkUpdateAndAutoTrackYAT(LocalDate today) {
        return executeBulk(FundType.YAT, today, dto -> true, this::persistYat);
    }

    private boolean persistByf(TefasFundDto dto) {
        // A BYF is an exchange-traded fund. On a given day TEFAS often omits its NAV (fiyat) while the exchange
        // bulletin price is present — gating ETFs on NAV alone silently DROPPED those (only ~10 of ~30 had a NAV
        // that day, all from one issuer). A real, listed ETF (one with a NAV OR a bulletin price) must still be
        // tracked so it shows in the list. We deliberately do NOT copy the bulletin into the NAV field: price
        // stays the genuine NAV (null until TEFAS publishes it — upsertCandleFromDto skips the candle while it is),
        // and bulletin_price keeps the exchange price. The next daily snapshot fills the NAV in cleanly once it
        // publishes, with no flip and nothing faked.
        if (!hasValidPrice(dto) && !hasValidBulletin(dto)) return false;
        Fund persisted = transactionTemplate.execute(s -> {
            Fund f = entityWriter.saveSnapshot(dto, FundType.BYF);
            entityWriter.upsertCandleFromDto(f, FundType.BYF, dto);
            return f;
        });
        if (persisted == null) return false;
        entityWriter.ensureByfTracked(persisted.getFundCode(), persisted.getName());
        fundCacheService.putSnapshot(persisted.getFundCode(), persisted);
        return true;
    }

    private static boolean hasValidBulletin(TefasFundDto dto) {
        return dto != null && dto.bulletinPrice() != null && dto.bulletinPrice().signum() != 0;
    }

    private boolean persistYat(TefasFundDto dto) {
        if (!hasValidPrice(dto)) return false;
        Fund persisted = transactionTemplate.execute(s -> {
            Fund f = entityWriter.saveSnapshot(dto, FundType.YAT);
            entityWriter.upsertCandleFromDto(f, FundType.YAT, dto);
            return f;
        });
        if (persisted == null) return false;
        entityWriter.ensureYatTracked(persisted.getFundCode(), persisted.getName());
        fundCacheService.putSnapshot(persisted.getFundCode(), persisted);
        return true;
    }

    private static boolean hasValidPrice(TefasFundDto dto) {
        return dto != null && dto.price() != null && dto.price().signum() != 0;
    }

    /** A row carries a usable snapshot price — a NAV, or (for an exchange-traded BYF) a bulletin price. */
    private static boolean hasUsablePrice(TefasFundDto dto) {
        return hasValidPrice(dto)
                || (dto != null && dto.bulletinPrice() != null && dto.bulletinPrice().signum() != 0);
    }

    /**
     * Bulk-fetches one fund type over a short WINDOW (not a single day) and persists each fund's most recent
     * usable observation, so a fund whose latest-day NAV isn't published yet snapshots at its last available
     * price instead of being dropped — the cause of a fresh-DB cold start capturing only the funds that had
     * already published that day (e.g. 10 of 30 ETFs). Returns the saved count, or -1 if the breaker is open.
     */
    private int executeBulk(FundType fundType, LocalDate today,
                            Predicate<TefasFundDto> include,
                            Predicate<TefasFundDto> persist) {
        try {
            LocalDate from = today.minusDays(holidayLookbackDays);
            List<TefasFundDto> bulk = tefasClient.bulkFetch(fundType, from, today);
            Map<String, TefasFundDto> latestPerFund = new LinkedHashMap<>();
            for (TefasFundDto dto : bulk) {
                if (dto == null || dto.fundCode() == null || !include.test(dto) || !hasUsablePrice(dto)) {
                    continue;
                }
                TefasFundDto prev = latestPerFund.get(dto.fundCode());
                if (prev == null || isNewer(dto, prev)) {
                    latestPerFund.put(dto.fundCode(), dto);
                }
            }
            int saved = 0;
            for (TefasFundDto dto : latestPerFund.values()) {
                try {
                    if (persist.test(dto)) saved++;
                } catch (Exception e) {
                    log.error("Failed to persist {} snapshot for fund {}", fundType, dto.fundCode(), e);
                }
            }
            log.info("Bulk {} snapshot: {} rows fetched, {} distinct funds, {} saved",
                    fundType, bulk.size(), latestPerFund.size(), saved);
            return saved;
        } catch (CallNotPermittedException e) {
            log.warn("TEFAS circuit breaker is OPEN, aborting {} snapshot", fundType);
            return -1;
        }
    }

    /** True when {@code candidate} is a strictly more recent observation than {@code current} (null dates last). */
    private static boolean isNewer(TefasFundDto candidate, TefasFundDto current) {
        if (candidate.date() == null) return false;
        if (current.date() == null) return true;
        return candidate.date().isAfter(current.date());
    }
}
