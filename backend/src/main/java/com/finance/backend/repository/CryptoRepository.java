package com.finance.backend.repository;

import com.finance.backend.model.Crypto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Crypto snapshot data
 */
@Repository
public interface CryptoRepository extends JpaRepository<Crypto, String> {
}
