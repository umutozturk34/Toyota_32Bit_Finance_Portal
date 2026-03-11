package com.finance.backend.repository;
import com.finance.backend.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {
    @Query("SELECT s.symbol FROM Stock s")
    List<String> findAllSymbols();
}
