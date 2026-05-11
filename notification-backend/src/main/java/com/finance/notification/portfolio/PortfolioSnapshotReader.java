package com.finance.notification.portfolio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PortfolioSnapshotReader {

    private static final String BULK_AGGREGATE_QUERY = """
            SELECT
                latest.user_sub,
                COALESCE(SUM(latest.total_value_try), 0)  AS total_value,
                COALESCE(SUM(latest.daily_pnl_try), 0)    AS daily_pnl,
                COUNT(latest.portfolio_id)                AS portfolio_count
            FROM (
                SELECT DISTINCT ON (s.portfolio_id)
                       p.user_sub,
                       s.portfolio_id,
                       s.total_value_try,
                       s.daily_pnl_try
                FROM portfolio_daily_snapshots s
                JOIN portfolios p ON p.id = s.portfolio_id
                WHERE p.user_sub = ANY(?)
                  AND s.snapshot_date = CURRENT_DATE
                ORDER BY s.portfolio_id, s.created_at DESC
            ) latest
            GROUP BY latest.user_sub
            """;

    private final JdbcTemplate jdbcTemplate;

    public record AggregatedSnapshot(BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlPercent, int portfolioCount) {}

    @Transactional(readOnly = true)
    public Map<String, AggregatedSnapshot> findTodayAggregateForUsers(Collection<String> userSubs) {
        Set<String> subs = new HashSet<>();
        for (String sub : userSubs) {
            if (sub != null && !sub.isBlank()) subs.add(sub);
        }
        if (subs.isEmpty()) return Map.of();
        Map<String, AggregatedSnapshot> result = new HashMap<>(subs.size());
        jdbcTemplate.query(BULK_AGGREGATE_QUERY,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", subs.toArray())),
                (rs, n) -> {
                    AggregatedSnapshot snap = mapRow(rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getInt(4));
                    if (snap != null) result.put(rs.getString(1), snap);
                    return null;
                });
        return result;
    }

    private static AggregatedSnapshot mapRow(BigDecimal total, BigDecimal pnl, int count) {
        if (count == 0) return null;
        BigDecimal pct = computePercent(total, pnl);
        return new AggregatedSnapshot(total, pnl, pct, count);
    }

    private static BigDecimal computePercent(BigDecimal total, BigDecimal pnl) {
        if (total == null || pnl == null) return null;
        BigDecimal previous = total.subtract(pnl);
        if (previous.signum() == 0) return null;
        return pnl.divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
