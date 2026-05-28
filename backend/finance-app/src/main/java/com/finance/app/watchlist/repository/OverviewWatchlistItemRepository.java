package com.finance.app.watchlist.repository;

import com.finance.app.watchlist.model.OverviewWatchlistItem;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link OverviewWatchlistItem}, fetching a list's items in caller-specified order. */
public interface OverviewWatchlistItemRepository extends JpaRepository<OverviewWatchlistItem, Long> {

    List<OverviewWatchlistItem> findByWatchlistId(Long watchlistId, Sort sort);
}
