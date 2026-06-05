package com.finance.portfolio.model;

import com.finance.common.model.TrackedAsset;
import com.finance.shared.service.AssetPricingPort;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A single hypothetical spot lot: a buy of {@code quantity} at {@code entryPrice} on {@code entryDate},
 * optionally closed by an exit. Multiple lots may exist per asset; they are not netted.
 * <p>All prices ({@code entryPrice}/{@code exitPrice}) are stored already converted to TRY at the
 * respective date's FX rate, so PnL math stays currency-agnostic. The asset identity lives on the
 * linked {@link TrackedAsset}; {@code assetType}/{@code assetCode} are transient mirrors hydrated
 * on load for convenience.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "portfolio_positions",
        indexes = @Index(name = "idx_portfolio_positions_portfolio", columnList = "portfolio_id"))
public class PortfolioPosition {

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

    @Column(name = "exit_date")
    private LocalDateTime exitDate;

    @Column(name = "exit_price", precision = 19, scale = 4)
    private BigDecimal exitPrice;

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

    /** Pricing-lookup key (market type + code) used to fetch live/historical prices for this lot. */
    public AssetPricingPort.AssetKey toAssetKey() {
        return new AssetPricingPort.AssetKey(assetType.marketType(), assetCode);
    }

    /** Cost basis in TRY (entry price × quantity). */
    public BigDecimal entryValue() {
        return entryPrice.multiply(quantity).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /** Market value in TRY at the given unit price; zero when price is unknown. */
    public BigDecimal currentValue(BigDecimal currentPrice) {
        if (currentPrice == null) return BigDecimal.ZERO;
        return currentPrice.multiply(quantity).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /** Mutates entry attributes in place; null arguments leave the corresponding field unchanged. */
    public void updateLot(LocalDateTime newEntryDate, BigDecimal newEntryPrice, BigDecimal newQuantity) {
        if (newEntryDate != null) this.entryDate = newEntryDate;
        if (newEntryPrice != null) this.entryPrice = newEntryPrice;
        if (newQuantity != null) this.quantity = newQuantity;
    }

    public boolean isClosed() {
        return exitDate != null;
    }

    /** Records an exit; {@code price} must already be in TRY (converted at the exit date's FX rate). */
    public void closeWith(LocalDateTime when, BigDecimal price) {
        this.exitDate = when;
        this.exitPrice = price;
    }

    /** Clears the exit, returning the lot to open/held state. */
    public void reopen() {
        this.exitDate = null;
        this.exitPrice = null;
    }

    /** Realized PnL in TRY for a closed lot; zero while still open. */
    public BigDecimal realizedPnl() {
        if (exitPrice == null) return BigDecimal.ZERO;
        return exitPrice.subtract(entryPrice).multiply(quantity).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }
}
