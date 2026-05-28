package com.finance.market.bond.util;


import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.market.bond.model.Bond;
import com.finance.market.core.dto.internal.EvdsSerieResponse;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filters and parses EVDS bond series into tradable bonds: keeps TRT/TRD/TRB ISINs with a valid
 * {@code T<nn>} suffix and an unexpired maturity (de-duplicated by base ISIN), parsing maturity
 * dates from the serie name. Also derives ISIN/ORAN codes and sanitizes bad coupon-rate values.
 */
@Log4j2
public final class BondSerieFilterUtil {

    private static final DateTimeFormatter SERIE_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern DATES_PATTERN = Pattern
            .compile("\\(\\s*(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s*\\)");
    private static final Pattern ISIN_SUFFIX_PATTERN = Pattern.compile("T\\d{2}$");

    private BondSerieFilterUtil() {
    }

    /** Zeroes a spurious coupon rate: always for TRB (discounted) bonds, or when it equals days-to-maturity. */
    public static void sanitizeCouponRate(Bond bond, BondSnapshotDto dto) {
        if (dto.isinCode() != null && dto.isinCode().startsWith("TRB")) {
            log.debug("{} — TRB prefix, setting couponRate to 0", dto.isinCode());
            bond.setCouponRate(BigDecimal.ZERO);
            return;
        }

        BigDecimal coupon = bond.getCouponRate();
        if (coupon == null || coupon.compareTo(BigDecimal.ZERO) == 0)
            return;

        if (dto.maturityEnd() != null) {
            long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), dto.maturityEnd());
            if (remainingDays > 0 && coupon.longValue() == remainingDays) {
                log.info("{} — couponRate {} equals remaining days to maturity {}, resetting to 0",
                        dto.isinCode(), coupon, remainingDays);
                bond.setCouponRate(BigDecimal.ZERO);
            }
        }
    }

    /** Keeps one tradable bond per base ISIN: government/sukuk/discount prefixes, valid suffix, unexpired. */
    public static List<BondSerieDto> filter(List<EvdsSerieResponse> series) {
        LocalDate today = LocalDate.now();
        Map<String, BondSerieDto> unique = new LinkedHashMap<>();

        for (EvdsSerieResponse s : series) {
            String code = s.serieCode();
            if (code == null || code.endsWith(".ORAN"))
                continue;

            String isin = extractIsin(code);
            if (isin == null)
                continue;

            if (!isin.startsWith("TRT") && !isin.startsWith("TRD") && !isin.startsWith("TRB"))
                continue;
            if (!ISIN_SUFFIX_PATTERN.matcher(isin).find())
                continue;

            LocalDate[] dates = parseDates(s.serieName());
            if (dates == null)
                continue;

            LocalDate maturityEnd = dates[1];
            if (maturityEnd.isBefore(today))
                continue;

            String baseIsin = ISIN_SUFFIX_PATTERN.matcher(isin).replaceFirst("");
            unique.putIfAbsent(baseIsin, new BondSerieDto(isin, code, s.serieName(), dates[0], dates[1]));
        }

        List<BondSerieDto> result = List.copyOf(unique.values());
        log.debug("Bond filter: {} raw -> {} unique bonds", series.size(), result.size());
        return result;
    }

    public static String extractIsin(String serieCode) {
        String withoutPrefix = serieCode.startsWith("TP.") ? serieCode.substring(3) : serieCode;
        int dotIdx = withoutPrefix.indexOf('.');
        return dotIdx > 0 ? withoutPrefix.substring(0, dotIdx) : withoutPrefix;
    }

    /** EVDS rate (.ORAN) serie code for an ISIN. */
    public static String toOranCode(String isin) {
        return "TP." + isin + ".ORAN";
    }

    public static LocalDate[] parseDates(String serieName) {
        if (serieName == null)
            return null;
        Matcher m = DATES_PATTERN.matcher(serieName);
        if (!m.find())
            return null;
        try {
            LocalDate start = LocalDate.parse(m.group(1), SERIE_DATE_FMT);
            LocalDate end = LocalDate.parse(m.group(2), SERIE_DATE_FMT);
            return new LocalDate[] { start, end };
        } catch (Exception e) {
            log.warn("Failed to parse dates from serie name: '{}'", serieName);
            return null;
        }
    }

    public static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            batches.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return batches;
    }
}
