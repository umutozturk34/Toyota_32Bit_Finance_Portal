package com.finance.backend.repository;

import com.finance.backend.model.CryptoCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CryptoCandleRepository extends JpaRepository<CryptoCandle, Long> {
    
    List<CryptoCandle> findByCryptoIdOrderByCandleDateAsc(String cryptoId);
    
    boolean existsByCryptoId(String cryptoId);
    
    long countByCryptoId(String cryptoId);
    
    @Modifying
    @Transactional
    void deleteByCryptoId(String cryptoId);
    
    @Modifying
    @Transactional
    void deleteByCandleDateBefore(LocalDateTime date);

    Optional<CryptoCandle> findByCryptoIdAndCandleDate(String cryptoId, LocalDateTime candleDate);
}