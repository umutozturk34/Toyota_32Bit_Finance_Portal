package com.finance.notification.watchlist.repository;

import com.finance.notification.watchlist.model.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserSubOrderByIsDefaultDescCreatedAtAsc(String userSub);

    Optional<Watchlist> findByUserSubAndIsDefaultTrue(String userSub);

    long countByUserSub(String userSub);

    boolean existsByUserSubAndName(String userSub, String name);
}
