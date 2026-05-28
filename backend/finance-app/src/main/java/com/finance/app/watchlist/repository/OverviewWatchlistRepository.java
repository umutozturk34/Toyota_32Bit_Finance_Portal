package com.finance.app.watchlist.repository;

import com.finance.app.watchlist.model.OverviewWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link OverviewWatchlist}, with owner-scoped lookups (default-first ordering). */
public interface OverviewWatchlistRepository extends JpaRepository<OverviewWatchlist, Long> {

    Optional<OverviewWatchlist> findByIdAndUserSub(Long id, String userSub);

    List<OverviewWatchlist> findByUserSubOrderByIsDefaultDescIdAsc(String userSub);
}
