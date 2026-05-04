package com.finance.notification.watchlist.repository;

import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUserSubOrderByCreatedAtDesc(String userSub);

    List<WatchlistItem> findByWatchlistIdOrderByCreatedAtDesc(Long watchlistId);

    List<WatchlistItem> findByMarketType(MarketType marketType);

    Optional<WatchlistItem> findByWatchlistIdAndMarketTypeAndAssetCode(Long watchlistId,
                                                                       MarketType marketType,
                                                                       String assetCode);

    long countByWatchlistId(Long watchlistId);
}
