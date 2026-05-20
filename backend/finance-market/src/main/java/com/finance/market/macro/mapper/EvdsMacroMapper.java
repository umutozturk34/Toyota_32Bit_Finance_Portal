package com.finance.market.macro.mapper;

import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.macro.dto.internal.MacroObservation;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Log4j2
public class EvdsMacroMapper {

    private static final String TARIH_KEY = "Tarih";
    private static final DateTimeFormatter MONTHLY = DateTimeFormatter.ofPattern("yyyy-M");
    private static final DateTimeFormatter DAILY = DateTimeFormatter.ofPattern("d-M-yyyy");

    public List<MacroObservation> extract(EvdsDataResponse response, String serieCode) {
        if (response == null || response.items() == null) {
            return List.of();
        }
        String column = sanitize(serieCode);
        List<MacroObservation> observations = new ArrayList<>(response.items().size());
        for (Map<String, Object> item : response.items()) {
            LocalDate observedAt = parseDate(asString(item.get(TARIH_KEY)));
            BigDecimal value = parseValue(item.get(column));
            if (observedAt != null && value != null) {
                observations.add(new MacroObservation(observedAt, value));
            }
        }
        return observations;
    }

    private String sanitize(String serieCode) {
        return serieCode.replace('.', '_');
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (raw.matches("\\d{4}-\\d{1,2}")) {
                return YearMonth.parse(raw, MONTHLY).atEndOfMonth();
            }
            return LocalDate.parse(raw, DAILY);
        } catch (Exception ex) {
            log.debug("Unparseable EVDS date {}: {}", raw, ex.getMessage());
            return null;
        }
    }

    private BigDecimal parseValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty() || text.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(',', '.'));
        } catch (NumberFormatException ex) {
            log.debug("Unparseable EVDS value {}: {}", text, ex.getMessage());
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
