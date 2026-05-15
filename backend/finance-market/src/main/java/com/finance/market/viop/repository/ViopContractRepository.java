package com.finance.market.viop.repository;

import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ViopContractRepository
        extends JpaRepository<ViopContract, Long>, JpaSpecificationExecutor<ViopContract> {

    Optional<ViopContract> findBySymbol(String symbol);

    List<ViopContract> findByKindAndActiveTrue(ViopContractKind kind);

    @Query("select c from ViopContract c where c.active = true and c.expiryDate < :today")
    List<ViopContract> findExpired(@Param("today") LocalDate today);

    @Query("select c.symbol from ViopContract c where c.active = true")
    List<String> findActiveSymbols();

    @Query("select c.symbol from ViopContract c where c.active = true and c.lastPrice is null")
    List<String> findActiveSymbolsWithoutPrice();
}
