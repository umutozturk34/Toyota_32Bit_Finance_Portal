package com.finance.app.watchlist.model;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "watchlist_items")
public class OverviewWatchlistItem {

    @Id
    @Column(name = "id")
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

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @PostLoad
    void syncTransientsFromTrackedAsset() {
        if (trackedAsset != null) {
            this.marketType = trackedAsset.getAssetType().marketType();
            this.assetCode = trackedAsset.getAssetCode();
        }
    }
}
