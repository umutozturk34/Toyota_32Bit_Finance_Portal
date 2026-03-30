package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "portfolio_daily_snapshots")
public class PortfolioDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_value_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValueTry;

    @Column(name = "total_cost_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostTry;

    @Column(name = "total_pnl_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPnlTry;

    @Column(name = "pnl_percent", nullable = false, precision = 19, scale = 4)
    private BigDecimal pnlPercent;

    @Column(name = "cash_balance_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalanceTry;
}
