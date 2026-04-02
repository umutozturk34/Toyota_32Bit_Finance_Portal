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
@Entity
@Table(name = "portfolio_asset_daily_snapshots")
public class PortfolioAssetDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
