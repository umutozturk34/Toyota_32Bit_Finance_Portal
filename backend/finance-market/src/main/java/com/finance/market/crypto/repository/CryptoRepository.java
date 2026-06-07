package com.finance.market.crypto.repository;
import com.finance.market.crypto.model.Crypto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence access for {@link Crypto} entities, keyed by crypto id.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic, filterable
 * crypto listing/search queries.
 */
@Repository
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
}
