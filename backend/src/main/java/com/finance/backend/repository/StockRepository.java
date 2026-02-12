package com.finance.backend.repository;

import com.finance.backend.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {
    
    Optional<Stock> findBySymbol(String symbol);
}
