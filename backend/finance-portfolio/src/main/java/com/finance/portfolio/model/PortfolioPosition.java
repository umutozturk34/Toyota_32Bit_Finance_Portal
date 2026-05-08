package com.finance.portfolio.model;
import com.finance.portfolio.model.Portfolio;

import com.finance.portfolio.model.AssetType;


import com.finance.common.model.TrackedAsset;
import com.finance.common.service.AssetPricingPort;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "portfolio_positions",
        indexes = @Index(name = "idx_portfolio_positions_portfolio", columnList = "portfolio_id"))
public class PortfolioPosition {

    private static final int PRICE_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "portfolio_id", insertable = false, updatable = false)
    private Long portfolioId;

    @Transient
    private AssetType assetType;

    @Transient
    private String assetCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracked_asset_id", nullable = false)
    @Setter
    private TrackedAsset trackedAsset;

    @PostLoad
    void syncTransientsFromTrackedAsset() {
        if (trackedAsset != null) {
            this.assetType = AssetType.valueOf(trackedAsset.getAssetType().name());
            this.assetCode = trackedAsset.getAssetCode();
        }
    }

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    public AssetPricingPort.AssetKey toAssetKey() {
        return new AssetPricingPort.AssetKey(assetType.marketType(), assetCode);
    }

    public BigDecimal entryValue() {
        return entryPrice.multiply(quantity).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal currentValue(BigDecimal currentPrice) {
        if (currentPrice == null) return BigDecimal.ZERO;
        return currentPrice.multiply(quantity).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal unrealizedPnl(BigDecimal currentPrice) {
        if (currentPrice == null) return BigDecimal.ZERO;
        return currentPrice.subtract(entryPrice).multiply(quantity).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public void updateLot(LocalDateTime newEntryDate, BigDecimal newEntryPrice, BigDecimal newQuantity) {
        if (newEntryDate != null) this.entryDate = newEntryDate;
        if (newEntryPrice != null) this.entryPrice = newEntryPrice;
        if (newQuantity != null) this.quantity = newQuantity;
    }
}
