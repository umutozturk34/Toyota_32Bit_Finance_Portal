package com.finance.notification.watchlist.model;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "watchlist_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_watchlist_items_list_tracked_asset",
                columnNames = {"watchlist_id", "tracked_asset_id"})
})
public class WatchlistItem {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "watchlist_id", nullable = false)
    private Long watchlistId;

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

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "delta_threshold", precision = 7, scale = 4)
    private BigDecimal deltaThreshold;

    @Column(name = "last_seen_price", precision = 19, scale = 4)
    private BigDecimal lastSeenPrice;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean belongsTo(String candidateUserSub) {
        return userSub.equals(candidateUserSub);
    }

    public Optional<BigDecimal> deltaPercent(BigDecimal currentPrice) {
        if (currentPrice == null || lastSeenPrice == null || lastSeenPrice.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal pct = currentPrice.subtract(lastSeenPrice)
                .divide(lastSeenPrice, 8, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
        return Optional.of(pct);
    }

    public boolean exceedsThreshold(BigDecimal currentPrice, BigDecimal globalDefault) {
        BigDecimal effective = deltaThreshold != null ? deltaThreshold : globalDefault;
        return deltaPercent(currentPrice)
                .map(BigDecimal::abs)
                .map(delta -> effective.signum() == 0 ? delta.signum() > 0 : delta.compareTo(effective) >= 0)
                .orElse(false);
    }

    public void recordObservation(BigDecimal currentPrice) {
        if (currentPrice != null) {
            this.lastSeenPrice = currentPrice;
            this.lastSeenAt = LocalDateTime.now();
        }
    }
}
