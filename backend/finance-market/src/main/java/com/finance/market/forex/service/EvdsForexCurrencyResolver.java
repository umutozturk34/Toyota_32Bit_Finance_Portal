package com.finance.market.forex.service;

import com.finance.market.core.dto.internal.EvdsSerieResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers the active forex currencies from EVDS serie metadata: keeps döviz buying series whose
 * end date is recent, parses currency code/name/unit from the serie name, and flags which also
 * have an "efektif" (cash) series. A currency is "active" unless its series ended over 6 months ago.
 */
@Log4j2
@Component
public class EvdsForexCurrencyResolver {

    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Pattern NAME_PATTERN = Pattern
            .compile("^\\(([A-Z]+)\\)\\s*(?:(\\d+)\\s+)?([^(]+?)\\s*\\(.*$");
    private static final String DOVIZ_BUYING_SUFFIX = ".A.YTL";
    private static final String EFEKTIF_BUYING_SUFFIX = ".A.EF.YTL";

    /** Returns one {@link ForexSerieMetadata} per active currency, de-duplicated by currency code. */
    public List<ForexSerieMetadata> resolveActive(List<EvdsSerieResponse> dovizSeries,
                                                  List<EvdsSerieResponse> efektifSeries) {
        Set<String> efektifCurrencies = extractActiveCurrencyCodes(efektifSeries, EFEKTIF_BUYING_SUFFIX);

        LocalDate today = LocalDate.now();
        Map<String, ForexSerieMetadata> active = new LinkedHashMap<>();
        for (EvdsSerieResponse serie : dovizSeries) {
            String code = serie.serieCode();
            if (code == null || !code.endsWith(DOVIZ_BUYING_SUFFIX)) continue;
            if (!isActive(serie, today)) continue;

            ParsedName parsedTr = parseName(serie.serieName());
            ParsedName parsedEn = parseName(serie.serieNameEng());
            if (parsedTr == null || parsedEn == null) continue;

            String currencyCode = parsedTr.code();
            int unit = parsedTr.unit() > 0 ? parsedTr.unit() : 1;
            boolean hasEfektif = efektifCurrencies.contains(currencyCode);

            active.putIfAbsent(currencyCode,
                    new ForexSerieMetadata(currencyCode, parsedEn.name(), parsedTr.name(), unit, hasEfektif));
        }
        log.info("Resolved {} active forex currencies from EVDS metadata", active.size());
        return new ArrayList<>(active.values());
    }

    /** Whether the döviz buying serie for one currency exists and is still active (validates a code before tracking). */
    public boolean isActiveCurrencyCode(List<EvdsSerieResponse> dovizSeries, String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return false;
        String normalized = currencyCode.trim().toUpperCase();
        LocalDate today = LocalDate.now();
        for (EvdsSerieResponse serie : dovizSeries) {
            String code = serie.serieCode();
            if (code == null) continue;
            if (!code.equals("TP.DK." + normalized + DOVIZ_BUYING_SUFFIX)) continue;
            return isActive(serie, today);
        }
        return false;
    }

    private Set<String> extractActiveCurrencyCodes(List<EvdsSerieResponse> series, String suffix) {
        Set<String> result = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (EvdsSerieResponse serie : series) {
            String code = serie.serieCode();
            if (code == null || !code.endsWith(suffix)) continue;
            if (!isActive(serie, today)) continue;
            String currencyCode = extractCurrencyCode(code);
            if (currencyCode != null) result.add(currencyCode);
        }
        return result;
    }

    /** A serie is active when it has no end date or ended within the last 6 months. */
    private boolean isActive(EvdsSerieResponse serie, LocalDate today) {
        String endDate = serie.endDate();
        if (endDate == null || endDate.isBlank()) return true;
        try {
            LocalDate end = LocalDate.parse(endDate, EVDS_DATE_FMT);
            return !end.isBefore(today.minusMonths(6));
        } catch (Exception ex) {
            log.debug("Cannot parse END_DATE='{}', treating as active", endDate);
            return true;
        }
    }

    private String extractCurrencyCode(String serieCode) {
        String[] parts = serieCode.split("\\.");
        return parts.length >= 5 ? parts[2] : null;
    }

    /** Extracts currency code, optional unit multiplier, and display name from an EVDS serie name. */
    private ParsedName parseName(String serieName) {
        if (serieName == null) return null;
        String cleaned = serieName.replaceAll("\\s+", " ").trim();
        Matcher m = NAME_PATTERN.matcher(cleaned);
        if (!m.matches()) {
            log.debug("Cannot parse SERIE_NAME pattern: '{}'", cleaned);
            return null;
        }
        String code = m.group(1);
        String unitStr = m.group(2);
        String name = m.group(3).trim();
        int unit = unitStr != null ? Integer.parseInt(unitStr) : 1;
        return new ParsedName(code, name, unit);
    }

    private record ParsedName(String code, String name, int unit) {
    }
}
