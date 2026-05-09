package com.finance.market.crypto.repository;
import com.finance.market.crypto.model.Crypto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CryptoRepository extends JpaRepository<Crypto, String>, JpaSpecificationExecutor<Crypto> {
    @Query("SELECT c.id FROM Crypto c")
    List<String> findAllIds();
}
