package com.finance.market.crypto.repository;
import com.finance.market.crypto.model.Crypto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Persistence access for {@link Crypto} entities, keyed by crypto id.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic, filterable
 * crypto listing/search queries.
 */
public interface CryptoRepository extends JpaRepository<Crypto, String>, JpaSpecificationExecutor<Crypto> {
    /**
     * Projects only the ids (primary keys) of all crypto assets.
     *
     * <p>Lightweight lookup used to drive batch refresh/iteration without
     * loading full entities.
     *
     * @return every persisted crypto id
     */
    @Query("SELECT c.id FROM Crypto c")
    List<String> findAllIds();

    /**
     * Projects {@code [id, name, symbol]} for every crypto — used by the news↔asset matcher to link articles that
     * name a coin (by name e.g. "Bitcoin", or parenthesised symbol e.g. "(BTC)") to the right asset.
     */
    @Query("SELECT c.id, c.name, c.symbol FROM Crypto c")
    List<Object[]> findAllIdsNamesAndSymbols();
}
