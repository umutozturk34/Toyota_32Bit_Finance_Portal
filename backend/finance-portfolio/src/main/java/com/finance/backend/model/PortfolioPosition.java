package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "portfolio_positions",
        indexes = @Index(name = "idx_portfolio_positions_portfolio", columnList = "portfolio_id"))
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "portfolio_id", insertable = false, updatable = false)
    private Long portfolioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 50)
    private AssetType assetType;

    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "average_cost_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageCostTry;

    @Column(name = "total_cost_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostTry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private static final int PRICE_SCALE = 4;
    private static final int QTY_SCALE = 8;

    public static PortfolioPosition empty(Portfolio portfolio, AssetType assetType, String assetCode) {
        return PortfolioPosition.builder()
                .portfolio(portfolio)
                .assetType(assetType)
                .assetCode(assetCode)
                .quantity(BigDecimal.ZERO)
                .averageCostTry(BigDecimal.ZERO)
                .totalCostTry(BigDecimal.ZERO)
                .build();
    }

    public boolean hasSufficientQuantity(BigDecimal amount) {
        return quantity.compareTo(amount) >= 0;
    }

    public void addQuantity(BigDecimal qty, BigDecimal cost) {
        BigDecimal newQty = quantity.add(qty).setScale(QTY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newTotalCost = totalCostTry.add(cost);
        BigDecimal newAvgCost = newQty.compareTo(BigDecimal.ZERO) > 0
                ? newTotalCost.divide(newQty, PRICE_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        this.quantity = newQty;
        this.totalCostTry = newTotalCost;
        this.averageCostTry = newAvgCost;
    }

    public void removeQuantity(BigDecimal qty) {
        BigDecimal costReduction = averageCostTry.multiply(qty).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        this.quantity = quantity.subtract(qty).setScale(QTY_SCALE, RoundingMode.HALF_UP);
        this.totalCostTry = totalCostTry.subtract(costReduction).max(BigDecimal.ZERO);
    }

    public BigDecimal calculateRealizedPnl(BigDecimal quantityToSell, BigDecimal proceeds, BigDecimal fee) {
        BigDecimal costBasis = averageCostTry.multiply(quantityToSell).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return proceeds.subtract(costBasis).subtract(fee);
    }
}
