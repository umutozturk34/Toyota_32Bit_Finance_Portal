package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the curated universe of instruments that {@link InflationBeaterService} ranks: every enabled
 * tracked stock/crypto/forex/fund/commodity plus every published deposit tenor. This is a distinct
 * responsibility from the ranking/cache orchestration — it only reads the tracked-asset and macro catalogs
 * and touches no scenario simulation or cache — so it lives in its own collaborator and the ranking service
 * simply asks it for the universe and a display-name lookup. The membership depends solely on the catalogs,
 * never on the period/currency/benchmark, so it is rebuilt cheaply on demand rather than cached here.
 *
 * <p>A Spring-managed collaborator injected into {@link InflationBeaterService}.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class InflationBeaterUniverseBuilder {

    private static final Map<TrackedAssetType, AnalyticsInstrumentType> TYPE_MAP = Map.of(
            TrackedAssetType.STOCK,     AnalyticsInstrumentType.SPOT,
            TrackedAssetType.CRYPTO,    AnalyticsInstrumentType.CRYPTO,
            TrackedAssetType.FOREX,     AnalyticsInstrumentType.FOREX,
            TrackedAssetType.FUND,      AnalyticsInstrumentType.FUND,
            TrackedAssetType.COMMODITY, AnalyticsInstrumentType.COMMODITY
    );

    private final MacroIndicatorQueryService macroQueryService;
    private final TrackedAssetQueryService trackedAssetQueryService;

    /** Curated universe member to rank — its analytics type, instrument code and display name. */
    public record CuratedAsset(AnalyticsInstrumentType type, String code, String name) {}

    /**
     * Builds the full curated universe: every enabled tracked asset of each mapped type, followed by every
     * rankable deposit tenor. A tracked type that fails to enumerate is logged and skipped, not fatal.
     */
    public List<CuratedAsset> buildUniverse() {
        List<CuratedAsset> universe = new ArrayList<>();
        for (var entry : TYPE_MAP.entrySet()) {
            TrackedAssetType trackedType = entry.getKey();
            AnalyticsInstrumentType analyticsType = entry.getValue();
            try {
                List<String> codes = trackedAssetQueryService.getEnabledCodes(trackedType);
                Map<String, String> names = trackedAssetQueryService.getDisplayNameMap(trackedType);
                for (String c : codes) {
                    universe.add(new CuratedAsset(analyticsType, c, names.getOrDefault(c, c)));
                }
            } catch (Exception e) {
                log.warn("Failed to enumerate tracked type={}: {}", trackedType, e.getMessage());
            }
        }
        universe.addAll(depositUniverse());
        return universe;
    }

    /**
     * Deposits enumerated from the macro catalog (every published TRY/USD/EUR tenor), not a hard-coded list —
     * mirroring how stocks/crypto/forex/commodity are enumerated, so newly added deposit tenors rank
     * automatically. The synthetic {@link DepositMaturity#TOTAL} bucket is excluded: it is the weighted
     * average across the other tenors, so ranking it beside them would double-count an aggregate that is not
     * itself a holdable product.
     */
    private List<CuratedAsset> depositUniverse() {
        List<CuratedAsset> deposits = new ArrayList<>();
        try {
            for (MacroIndicator d : macroQueryService.listByCategory(MacroCategory.DEPOSIT)) {
                if (d.getMaturity() == DepositMaturity.TOTAL) continue;
                deposits.add(new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, d.getCode(), depositName(d)));
            }
        } catch (Exception e) {
            log.warn("Failed to enumerate deposit universe: {}", e.getMessage());
        }
        return deposits;
    }

    /** Fallback display name "{CURRENCY} {tenor}" (e.g. "TRY 3M"); the frontend localizes via the label key. */
    private static String depositName(MacroIndicator d) {
        String currency = d.getCurrency() != null ? d.getCurrency() + " " : "";
        String tenor = d.getMaturity() != null ? d.getMaturity().tenorLabel() : d.getCode();
        return currency + tenor;
    }

    /** Type+code keyed display-name lookup for the universe, used to label ranked entries. */
    public Map<String, String> nameMapFor(List<CuratedAsset> universe) {
        Map<String, String> out = new LinkedHashMap<>();
        for (CuratedAsset c : universe) {
            out.put(c.type() + "|" + c.code(), c.name());
        }
        return out;
    }
}
