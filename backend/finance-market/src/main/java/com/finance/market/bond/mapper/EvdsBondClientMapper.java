package com.finance.market.bond.mapper;


import com.finance.market.bond.dto.external.BondRateItemDto;
import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.bond.util.BondSerieFilterUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Log4j2
public class EvdsBondClientMapper {

    private static final DateTimeFormatter EVDS_DATE_FMT = AbstractEvdsClient.DATE_FMT;

    public List<BondSnapshotDto> toSnapshotDtos(
            List<BondSerieDto> bondSeries,
            EvdsDataResponse response) {

        if (response.items() == null || response.items().isEmpty()) {
            log.warn("EVDS response has no items, returning empty snapshot list");
            return List.of();
        }

        Map<String, Object> latestItem = findLatestNonNullItem(response.items(), bondSeries);

        List<BondSnapshotDto> snapshots = new ArrayList<>();
        for (BondSerieDto serie : bondSeries) {
            String valueKey = serie.serieCode().replace(".", "_");
            String oranKey = BondSerieFilterUtil.toOranCode(serie.isin()).replace(".", "_");

            BigDecimal cleanPrice = extractBigDecimal(latestItem, valueKey);
            BigDecimal couponRate = extractBigDecimal(latestItem, oranKey);

            if (cleanPrice == null && couponRate == null) {
                log.debug("No data for bond {} (price key={}, rate key={}), skipping", serie.isin(), valueKey, oranKey);
                continue;
            }

            snapshots.add(new BondSnapshotDto(
                    serie.serieCode(),
                    serie.isin(),
                    cleanPrice,
                    couponRate,
                    serie.maturityStart(),
                    serie.maturityEnd(),
                    serie.serieName()
            ));
        }

        log.debug("Mapped {} snapshots from {} items ({} series input)", snapshots.size(), response.items().size(), bondSeries.size());
        return snapshots;
    }

    public List<BondRateItemDto> toRateItemDtos(EvdsDataResponse response, String oranCode) {
        if (response.items() == null || response.items().isEmpty()) {
            log.debug("No items in EVDS response for rate extraction (code={})", oranCode);
            return List.of();
        }

        String oranKey = oranCode.replace(".", "_");
        List<BondRateItemDto> rates = new ArrayList<>();

        for (Map<String, Object> item : response.items()) {
            BigDecimal rate = extractBigDecimal(item, oranKey);
            LocalDate date = extractDate(item);
            if (rate == null || date == null) continue;

            rates.add(new BondRateItemDto(date, rate));
        }

        log.debug("Extracted {} rate items from {} EVDS items (code={})", rates.size(), response.items().size(), oranCode);
        return rates;
    }

    private Map<String, Object> findLatestNonNullItem(
            List<Map<String, Object>> items,
            List<BondSerieDto> bondSeries) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Map<String, Object> item = items.get(i);
            for (BondSerieDto serie : bondSeries) {
                String key = serie.serieCode().replace(".", "_");
                if (item.get(key) != null) return item;
            }
        }
        log.warn("No non-null item found in {} items, using last item as fallback", items.size());
        return items.getLast();
    }

    private LocalDate extractDate(Map<String, Object> item) {
        Object raw = item.get("Tarih");
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw.toString().trim(), EVDS_DATE_FMT);
        } catch (Exception e) {
            log.warn("Failed to parse date from EVDS item: '{}'", raw);
            return null;
        }
    }

    static BigDecimal extractBigDecimal(Map<String, Object> item, String key) {
        Object raw = item.get(key);
        if (raw == null) return null;
        try {
            if (raw instanceof BigDecimal bd) {
                return bd;
            }
            if (raw instanceof Number num) {
                return new BigDecimal(num.toString());
            }
            String str = raw.toString().trim();
            if (str.isEmpty()) return null;
            return new BigDecimal(str);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal from EVDS field '{}': '{}'", key, raw);
            return null;
        }
    }
}
