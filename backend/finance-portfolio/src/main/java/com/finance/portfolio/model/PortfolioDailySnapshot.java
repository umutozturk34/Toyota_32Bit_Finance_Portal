package com.finance.portfolio.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Portfolio-level daily roll-up in TRY: total market value, cost, total/daily PnL and percentages
 * for one day. {@code cashTry} carries cumulative realized proceeds from closed positions (the model
 * holds no real cash). Drives the performance/history charts.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "portfolio_daily_snapshots",
        indexes = {
                @Index(name = "idx_portfolio_daily_snapshots_pf_date",
                        columnList = "portfolio_id, snapshot_date"),
                @Index(name = "idx_portfolio_daily_snapshots_pf_created",
                        columnList = "portfolio_id, created_at")
        })
public class PortfolioDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "portfolio_daily_snapshot_seq")
    @SequenceGenerator(name = "portfolio_daily_snapshot_seq", sequenceName = "portfolio_daily_snapshot_seq", allocationSize = 50)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_value_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValueTry;

    @Column(name = "cash_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashTry;

    @Column(name = "total_cost_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostTry;

    @Column(name = "total_pnl_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPnlTry;

    @Column(name = "pnl_percent", nullable = false, precision = 19, scale = 4)
    private BigDecimal pnlPercent;

    @Column(name = "daily_pnl_try", precision = 19, scale = 4)
    private BigDecimal dailyPnlTry;

    @Column(name = "daily_pnl_percent", precision = 19, scale = 4)
    private BigDecimal dailyPnlPercent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
