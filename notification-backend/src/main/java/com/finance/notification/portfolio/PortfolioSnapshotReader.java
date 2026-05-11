package com.finance.notification.portfolio;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PortfolioSnapshotReader {

    // DISTINCT ON yields one row per portfolio (latest by created_at) so SUM
    // does not double-count intraday snapshots after V55 dropped the per-day
    // unique constraint.
    private static final String AGGREGATE_QUERY = """
            SELECT
                COALESCE(SUM(latest.total_value_try), 0)  AS total_value,
                COALESCE(SUM(latest.daily_pnl_try), 0)    AS daily_pnl,
                COUNT(latest.portfolio_id)                AS portfolio_count
            FROM (
                SELECT DISTINCT ON (s.portfolio_id)
                       s.portfolio_id,
                       s.total_value_try,
                       s.daily_pnl_try
                FROM portfolio_daily_snapshots s
                JOIN portfolios p ON p.id = s.portfolio_id
                WHERE p.user_sub = ?
                  AND s.snapshot_date = CURRENT_DATE
                ORDER BY s.portfolio_id, s.created_at DESC
            ) latest
            """;

    private final JdbcTemplate jdbcTemplate;

    public record AggregatedSnapshot(BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlPercent, int portfolioCount) {}

    @Transactional(readOnly = true)
    public Optional<AggregatedSnapshot> findTodayAggregateForUser(String userSub) {
        if (userSub == null || userSub.isBlank()) return Optional.empty();
        try {
            AggregatedSnapshot row = jdbcTemplate.queryForObject(AGGREGATE_QUERY,
                    (rs, n) -> {
                        BigDecimal total = rs.getBigDecimal(1);
                        BigDecimal pnl = rs.getBigDecimal(2);
                        int count = rs.getInt(3);
                        if (count == 0) return null;
                        BigDecimal pct = computePercent(total, pnl);
                        return new AggregatedSnapshot(total, pnl, pct, count);
                    },
                    userSub);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal computePercent(BigDecimal total, BigDecimal pnl) {
        if (total == null || pnl == null) return null;
        BigDecimal previous = total.subtract(pnl);
        if (previous.signum() == 0) return null;
        return pnl.divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
