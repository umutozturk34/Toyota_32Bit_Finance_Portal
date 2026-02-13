package com.finance.backend.repository;

import com.finance.backend.model.Forex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ForexRepository extends JpaRepository<Forex, String> {
    
    Optional<Forex> findByCurrencyCode(String currencyCode);
    
    List<Forex> findAllByOrderByCurrencyCodeAsc();
    
    List<Forex> findByUpdatedAtBefore(LocalDateTime threshold);
}
