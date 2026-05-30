package com.finance.notification.macro;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the latest two observations of each requested macro indicator and computes the change
 * between them. FLAT (no movement) indicators are dropped so only meaningful deltas are returned,
 * and percent change is omitted when the previous value is missing or zero.
 */
@Service
@RequiredArgsConstructor
public class MacroIndicatorChangeReader {

    private static final String CHANGES_QUERY = """
            WITH ranked AS (
                SELECT p.indicator_id,
                       p.observed_at,
                       p.value,
                       ROW_NUMBER() OVER (PARTITION BY p.indicator_id ORDER BY p.observed_at DESC) AS rn
                FROM macro_indicator_points p
                JOIN macro_indicators i ON i.id = p.indicator_id
                WHERE i.code = ANY(?)
            )
            SELECT i.code, i.label, i.category, i.unit, i.currency, i.maturity,
                   curr.observed_at AS curr_date,
                   curr.value       AS curr_value,
                   prev.observed_at AS prev_date,
                   prev.value       AS prev_value
            FROM macro_indicators i
            JOIN ranked curr ON curr.indicator_id = i.id AND curr.rn = 1
            LEFT JOIN ranked prev ON prev.indicator_id = i.id AND prev.rn = 2
            WHERE i.code = ANY(?)
            """;

    private final JdbcTemplate jdbcTemplate;

    /** A single indicator's move from its previous to its current observation, with computed deltas. */
    public record IndicatorChange(
            String code,
            String label,
            String category,
            String unit,
            String currency,
            String maturity,
            LocalDate newDate,
            BigDecimal newValue,
            LocalDate previousDate,
            BigDecimal previousValue,
            BigDecimal deltaAbsolute,
            BigDecimal deltaPercent,
            Direction direction
    ) {
        public enum Direction { UP, DOWN, FLAT }
    }

    @Transactional(readOnly = true)
    public List<IndicatorChange> findChanges(Collection<String> codes) {
        Set<String> unique = new HashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) unique.add(code);
        }
        if (unique.isEmpty()) return List.of();
        Object[] codesArray = unique.toArray();
        List<IndicatorChange> rows = jdbcTemplate.query(CHANGES_QUERY,
                ps -> {
                    Array arr = ps.getConnection().createArrayOf("text", codesArray);
                    ps.setArray(1, arr);
                    ps.setArray(2, arr);
                },
                (rs, n) -> mapRow(rs.getString("code"),
                        rs.getString("label"),
                        rs.getString("category"),
                        rs.getString("unit"),
                        rs.getString("currency"),
                        rs.getString("maturity"),
                        rs.getObject("curr_date", LocalDate.class),
                        rs.getBigDecimal("curr_value"),
                        rs.getObject("prev_date", LocalDate.class),
                        rs.getBigDecimal("prev_value")));
        List<IndicatorChange> changed = new ArrayList<>(rows.size());
        for (IndicatorChange change : rows) {
            if (change == null) continue;
            if (change.direction() == IndicatorChange.Direction.FLAT) continue;
            changed.add(change);
        }
        return changed;
    }

    private static IndicatorChange mapRow(String code, String label, String category, String unit,
                                          String currency, String maturity,
                                          LocalDate currDate, BigDecimal currValue,
                                          LocalDate prevDate, BigDecimal prevValue) {
        if (currValue == null || currDate == null) return null;
        BigDecimal deltaAbs = prevValue == null ? null : currValue.subtract(prevValue);
        BigDecimal deltaPct = computePercent(prevValue, deltaAbs);
        IndicatorChange.Direction direction = directionOf(deltaAbs);
        return new IndicatorChange(code, label, category, unit, currency, maturity,
                currDate, currValue, prevDate, prevValue, deltaAbs, deltaPct, direction);
    }

    private static BigDecimal computePercent(BigDecimal previous, BigDecimal delta) {
        if (previous == null || delta == null) return null;
        if (previous.signum() == 0) return null;
        return delta.divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private static IndicatorChange.Direction directionOf(BigDecimal delta) {
        if (delta == null) return IndicatorChange.Direction.FLAT;
        int sign = delta.signum();
        if (sign > 0) return IndicatorChange.Direction.UP;
        if (sign < 0) return IndicatorChange.Direction.DOWN;
        return IndicatorChange.Direction.FLAT;
    }
}
