package com.finance.backend.repository;
import com.finance.backend.model.CryptoCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface CryptoCandleRepository extends JpaRepository<CryptoCandle, Long> {
    List<CryptoCandle> findByCryptoIdOrderByCandleDateAsc(String cryptoId);
    Optional<CryptoCandle> findFirstByCryptoIdOrderByCandleDateDesc(String cryptoId);
    boolean existsByCryptoId(String cryptoId);
    long countByCryptoId(String cryptoId);
    void deleteByCryptoId(String cryptoId);
    void deleteByCandleDateBefore(LocalDateTime date);
    Optional<CryptoCandle> findByCryptoIdAndCandleDate(String cryptoId, LocalDateTime candleDate);
    List<CryptoCandle> findByCryptoIdAndCandleDateIn(String cryptoId, List<LocalDateTime> candleDates);
}