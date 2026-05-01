package com.finance.backend.model;

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
                        columnList = "portfolio_id, created_at")
        })
public class PortfolioAssetDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 50)
    private AssetType assetType;

    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

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
