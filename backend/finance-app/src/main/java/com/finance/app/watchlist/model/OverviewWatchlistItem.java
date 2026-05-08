package com.finance.app.watchlist.model;

import com.finance.common.model.MarketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 16)
    private MarketType marketType;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
