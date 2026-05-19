package com.finance.portfolio.model;

import com.finance.common.model.TrackedAsset;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "portfolio_asset_daily_snapshots",
        indexes = {
                @Index(name = "idx_portfolio_asset_snapshots_pf_date",
                        columnList = "portfolio_id, snapshot_date"),
                @Index(name = "idx_portfolio_asset_snapshots_pf_created",
                        columnList = "portfolio_id, created_at"),
                @Index(name = "idx_portfolio_asset_snapshots_pf_tracked_created",
                        columnList = "portfolio_id, tracked_asset_id, created_at DESC"),
                @Index(name = "idx_portfolio_asset_snapshots_pf_type_code_created",
                        columnList = "portfolio_id, asset_type, asset_code, created_at DESC")
        })
public class PortfolioAssetDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "portfolio_asset_daily_snapshot_seq")
    @SequenceGenerator(name = "portfolio_asset_daily_snapshot_seq", sequenceName = "portfolio_asset_daily_snapshot_seq", allocationSize = 50)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 32)
    private AssetType assetType;

    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracked_asset_id")
    private TrackedAsset trackedAsset;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceTry;

    @Column(name = "market_value_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal marketValueTry;

    @Column(name = "total_cost_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostTry;

    @Column(name = "pnl_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal pnlTry;

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
