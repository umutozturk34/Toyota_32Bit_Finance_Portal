package com.finance.market.core.util;

import com.finance.market.core.dto.internal.EvdsDataResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges several EVDS data responses — each fetched for a DISJOINT subset of series codes over the SAME date
 * range — back into a single response, combining all series columns per date. Used to chunk an otherwise
 * oversized single request (every active forex currency at once) into small reliable requests and stitch the
 * results so downstream mappers see one logical response. The merged rows are ordered by ascending {@code Tarih}
 * so the latest-row scan and earliest-date lookup keep working.
 */
public final class EvdsResponseMerger {

    /** EVDS dates are {@code dd-MM-yyyy}; sorting the raw strings would order by day, so parse before sorting. */
    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String DATE_KEY = "Tarih";

    private EvdsResponseMerger() {
    }

    /**
     * Combines the given responses into one. Rows are keyed by their {@code Tarih} value and merged column-wise,
     * so each date carries every chunk's series values; dateless rows are dropped (they are unusable downstream).
     * The result's {@code totalCount} is the sum of the parts'.
     *
     * @param parts the per-chunk responses (nulls and null item lists are tolerated)
     * @return a single merged, date-ascending response
     */
    public static EvdsDataResponse mergeByDate(List<EvdsDataResponse> parts) {
        LinkedHashMap<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        int totalCount = 0;
        for (EvdsDataResponse part : parts) {
            if (part == null) continue;
            totalCount += part.totalCount();
            if (part.items() == null) continue;
            for (Map<String, Object> item : part.items()) {
                Object tarih = item.get(DATE_KEY);
                if (tarih == null) continue;
                byDate.computeIfAbsent(tarih.toString(), k -> new LinkedHashMap<>()).putAll(item);
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>(byDate.values());
        merged.sort(Comparator.comparing(row -> parseDate(row.get(DATE_KEY))));
        return new EvdsDataResponse(totalCount, merged);
    }

    private static LocalDate parseDate(Object raw) {
        try {
            return LocalDate.parse(raw.toString().trim(), EVDS_DATE_FMT);
        } catch (RuntimeException ex) {
            return LocalDate.MIN;
        }
    }
}
