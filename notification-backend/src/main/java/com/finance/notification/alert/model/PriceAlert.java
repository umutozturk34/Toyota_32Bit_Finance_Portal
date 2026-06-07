package com.finance.notification.alert.model;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A user's price alert on a tracked asset. The threshold is stored in the asset's native currency;
 * {@code marketType}/{@code assetCode} are transient projections of the linked tracked asset. An
 * alert evaluates only while active and untriggered, and deactivates itself once it fires.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "price_alerts",
        indexes = {
                @Index(name = "idx_price_alerts_user_created", columnList = "user_sub, created_at DESC"),
                @Index(name = "idx_price_alerts_active_tracked", columnList = "tracked_asset_id")
        })
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Transient
    private MarketType marketType;

    @Transient
    private String assetCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracked_asset_id", nullable = false)
    private TrackedAsset trackedAsset;

    @PostLoad
    void syncTransientsFromTrackedAsset() {
        if (trackedAsset != null) {
            this.marketType = trackedAsset.getAssetType().marketType();
            this.assetCode = trackedAsset.getAssetCode();
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private AlertDirection direction;

    @Column(name = "threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "reference_price", precision = 19, scale = 4)
    private BigDecimal referencePrice;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (currency == null || currency.isBlank()) currency = "TRY";
    }

    /** Ownership guard used to authorize per-user access before exposing or mutating the alert. */
    public boolean belongsTo(String candidateUserSub) {
        return userSub.equals(candidateUserSub);
    }

    /** True only while the alert is eligible to fire, i.e. still active and not yet triggered. */
    public boolean shouldEvaluate() {
        return active && triggeredAt == null;
    }

    /** Returns whether the alert fires at {@code currentPrice} (TRY); false when ineligible or price is null. */
    public boolean evaluate(BigDecimal currentPrice) {
        if (!shouldEvaluate() || currentPrice == null) {
            return false;
        }
        return direction.isFired(currentPrice, referencePrice, threshold);
    }

    /** Records the firing instant and deactivates the alert so it cannot fire again. */
    public void markFired() {
        this.triggeredAt = LocalDateTime.now();
        this.active = false;
    }

    /** Clears the triggered state and re-arms the alert so it can fire on a future crossing. */
    public void reactivate() {
        this.triggeredAt = null;
        this.active = true;
    }
}
