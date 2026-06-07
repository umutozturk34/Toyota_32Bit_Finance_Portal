package com.finance.app.service.overview;

import com.finance.app.analytics.dto.RiskLevel;
import com.finance.app.analytics.dto.response.AssetReturnRow;
import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import com.finance.app.analytics.dto.response.PeriodReturn;
import com.finance.app.analytics.service.AssetReturnsService;
import com.finance.app.dto.response.overview.AssetReturnsData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides the ASSET_RETURNS widget from the cached asset-returns dataset: on a cold cache it triggers an
 * async warm and returns empty (the client retries), otherwise it slices the section's window, applies the
 * type/risk filters, sorts by the chosen key/direction and limits the rows. Figures are expressed in the
 * widget's OWN currency (TRY/USD/EUR) from the dataset's per-currency figures, so each widget's ranking is
 * independent — TRY, USD and EUR rank differently because the FX move differs per asset.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class AssetReturnsWidgetProvider implements OverviewWidgetProvider {

    private static final String DEFAULT_PERIOD = "1Y";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MIN_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final String FILTER_ALL = "ALL";
    private static final String SORT_RETURN = "RETURN";
    private static final String SORT_RISK_ADJ = "RISK_ADJ";
    private static final String SORT_VOLATILITY = "VOLATILITY";
    private static final String SORT_DESC = "DESC";
    private static final String SORT_ASC = "ASC";
    private static final String CCY_TRY = "TRY";
    private static final String CCY_USD = "USD";
    private static final String CCY_EUR = "EUR";

    private final AssetReturnsService assetReturnsService;

    @Override
    public WidgetKind kind() {
        return WidgetKind.ASSET_RETURNS;
    }

    @Override
    public AssetReturnsData fetch(String userSub, WidgetSection section) {
        String period = readPeriod(section);
        Set<String> typeFilter = readUpperSet(section, "assetType");
        Set<String> riskFilter = readUpperSet(section, "risk");
        String sortBy = readSortBy(section);
        boolean ascending = SORT_ASC.equalsIgnoreCase(readSortDir(section));
        int limit = readLimit(section);
        String currency = readCurrency(section);

        AssetReturnsResponse dataset;
        try {
            dataset = assetReturnsService.peekReturns();
        } catch (RuntimeException ex) {
            log.warn("AssetReturnsWidget peek failed period={}: {}", period, ex.getMessage());
            return new AssetReturnsData(period, currency, null, sortBy, List.of());
        }
        if (dataset == null) {
            assetReturnsService.warmAsync();
            log.debug("AssetReturnsWidget cold cache, async warm triggered period={}", period);
            return new AssetReturnsData(period, currency, null, sortBy, List.of());
        }

        List<AssetReturnsData.ReturnRow> rows = buildRows(
                dataset, period, currency, typeFilter, riskFilter, sortBy, ascending, limit);
        return new AssetReturnsData(period, currency, dataset.asOf(), sortBy, rows);
    }

    private List<AssetReturnsData.ReturnRow> buildRows(AssetReturnsResponse dataset, String period, String currency,
                                                       Set<String> typeFilter, Set<String> riskFilter,
                                                       String sortBy, boolean ascending, int limit) {
        List<AssetReturnsData.ReturnRow> rows = new ArrayList<>();
        for (AssetReturnRow asset : dataset.assets()) {
            PeriodReturn pr = asset.periods() == null ? null : asset.periods().get(period);
            // The dataset omits windows an asset's history doesn't cover (the ±30-day tolerance), so a missing
            // period just means this asset isn't ranked for it.
            if (pr == null || pr.returnPct() == null) continue;
            CurrencyView view = view(pr, currency);
            // No figure in this currency (FX history doesn't reach the window) → drop from THIS currency's ranking.
            if (view == null || view.returnPct() == null) continue;
            String type = asset.type() != null ? asset.type().name() : "";
            if (!typeFilter.isEmpty() && !typeFilter.contains(type.toUpperCase())) continue;
            String risk = view.riskLevel() != null ? view.riskLevel().name() : null;
            if (!riskFilter.isEmpty() && (risk == null || !riskFilter.contains(risk))) continue;
            rows.add(new AssetReturnsData.ReturnRow(
                    type, asset.code(), asset.name(),
                    view.returnPct(), view.returnChange(), view.priceNow(), view.volatility(), risk));
        }
        Comparator<AssetReturnsData.ReturnRow> ascending0 =
                Comparator.comparingDouble(r -> sortKey(r, sortBy));
        rows.sort(ascending ? ascending0 : ascending0.reversed());
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    /**
     * The window's figures in the requested currency: TRY is the dataset's top level, USD/EUR each their own
     * pre-converted block (null when FX history doesn't cover the window). Lets one widget rank in USD while
     * another ranks in TRY off the same cached dataset.
     */
    private static CurrencyView view(PeriodReturn pr, String currency) {
        if (CCY_USD.equals(currency)) {
            PeriodReturn.CurrencyFigures f = pr.usd();
            return f == null ? null
                    : new CurrencyView(f.returnPct(), f.returnValue(), f.priceNow(), f.volatility(), f.riskLevel());
        }
        if (CCY_EUR.equals(currency)) {
            PeriodReturn.CurrencyFigures f = pr.eur();
            return f == null ? null
                    : new CurrencyView(f.returnPct(), f.returnValue(), f.priceNow(), f.volatility(), f.riskLevel());
        }
        return new CurrencyView(pr.returnPct(), pr.returnTry(), pr.priceNow(), pr.volatility(), pr.riskLevel());
    }

    private record CurrencyView(BigDecimal returnPct, BigDecimal returnChange, BigDecimal priceNow,
                                BigDecimal volatility, RiskLevel riskLevel) {}

    /**
     * Sort key for a row. Rows with no usable figure (e.g. unknown volatility for the risk-adjusted/volatility
     * sorts) sink to {@code -inf} so they land at the bottom of a descending list, matching the Returns page.
     */
    private static double sortKey(AssetReturnsData.ReturnRow r, String sortBy) {
        if (SORT_RISK_ADJ.equals(sortBy)) {
            if (r.returnPct() == null || r.volatility() == null || r.volatility().signum() <= 0) {
                return Double.NEGATIVE_INFINITY;
            }
            return r.returnPct().doubleValue() / r.volatility().doubleValue();
        }
        if (SORT_VOLATILITY.equals(sortBy)) {
            return r.volatility() != null ? r.volatility().doubleValue() : Double.NEGATIVE_INFINITY;
        }
        return r.returnPct() != null ? r.returnPct().doubleValue() : Double.NEGATIVE_INFINITY;
    }

    private String readPeriod(WidgetSection section) {
        JsonNode node = section.config().get("period");
        if (node == null || node.isNull()) return DEFAULT_PERIOD;
        String value = node.asString(null);
        return (value == null || value.isBlank()) ? DEFAULT_PERIOD : value.trim().toUpperCase();
    }

    private String readSortBy(WidgetSection section) {
        JsonNode node = section.config().get("sortBy");
        if (node == null || node.isNull()) return SORT_RETURN;
        String value = node.asString(null);
        if (value == null) return SORT_RETURN;
        String upper = value.trim().toUpperCase();
        if (SORT_RISK_ADJ.equals(upper) || SORT_VOLATILITY.equals(upper)) return upper;
        return SORT_RETURN;
    }

    private String readSortDir(WidgetSection section) {
        JsonNode node = section.config().get("sortDir");
        if (node == null || node.isNull()) return SORT_DESC;
        String value = node.asString(null);
        return SORT_ASC.equalsIgnoreCase(value) ? SORT_ASC : SORT_DESC;
    }

    private int readLimit(WidgetSection section) {
        JsonNode node = section.config().get("limit");
        if (node == null || !node.isInt()) return DEFAULT_LIMIT;
        int requested = node.asInt();
        if (requested < MIN_LIMIT) return MIN_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }

    /** The widget's own display currency (TRY/USD/EUR); defaults to TRY and is independent of every other widget. */
    private String readCurrency(WidgetSection section) {
        JsonNode node = section.config().get("currency");
        if (node == null || node.isNull()) return CCY_TRY;
        String value = node.asString(null);
        if (value == null) return CCY_TRY;
        String upper = value.trim().toUpperCase();
        return (CCY_USD.equals(upper) || CCY_EUR.equals(upper)) ? upper : CCY_TRY;
    }

    /** Reads a config value as an upper-cased set (array or comma-separated string), dropping blanks and "ALL". */
    private Set<String> readUpperSet(WidgetSection section, String key) {
        JsonNode node = section.config().get(key);
        if (node == null || node.isNull()) return Set.of();
        Set<String> out = new HashSet<>();
        if (node.isArray()) {
            node.forEach(n -> {
                String v = n.asString(null);
                if (v != null && !v.isBlank() && !FILTER_ALL.equalsIgnoreCase(v)) {
                    out.add(v.trim().toUpperCase());
                }
            });
            return out;
        }
        String value = node.asString(null);
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank() && !FILTER_ALL.equalsIgnoreCase(s))
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }
}
