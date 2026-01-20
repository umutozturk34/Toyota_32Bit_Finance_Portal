package com.finance.backend.repository;

import com.finance.backend.entity.CryptoPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CryptoPriceRepository extends JpaRepository<CryptoPrice, Long> {
    
    @Query("SELECT c FROM CryptoPrice c WHERE c.timestamp = (SELECT MAX(c2.timestamp) FROM CryptoPrice c2 WHERE c2.symbol = c.symbol) ORDER BY c.marketCapRank")
    Page<CryptoPrice> findLatestPrices(Pageable pageable);
    
    Optional<CryptoPrice> findFirstBySymbolOrderByTimestampDesc(String symbol);
    
    List<CryptoPrice> findBySymbolOrderByTimestampDesc(String symbol);
}
