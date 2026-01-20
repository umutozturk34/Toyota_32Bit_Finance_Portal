package com.finance.backend.repository;

import com.finance.backend.entity.MetalPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetalPriceRepository extends JpaRepository<MetalPrice, Long> {
    
    @Query("SELECT mp FROM MetalPrice mp WHERE mp.id IN " +
           "(SELECT MAX(mp2.id) FROM MetalPrice mp2 GROUP BY mp2.symbol)")
    List<MetalPrice> findLatestPrices();
    
    @Query("SELECT mp FROM MetalPrice mp WHERE mp.id IN " +
           "(SELECT MAX(mp2.id) FROM MetalPrice mp2 GROUP BY mp2.symbol)")
    Page<MetalPrice> findLatestPrices(Pageable pageable);
    
    List<MetalPrice> findBySymbolOrderByTimestampDesc(String symbol);
}