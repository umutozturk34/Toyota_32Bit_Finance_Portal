package com.finance.backend.repository;
import com.finance.backend.model.Forex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForexRepository extends JpaRepository<Forex, String>, JpaSpecificationExecutor<Forex> {
    List<Forex> findAllByOrderByCurrencyCodeAsc();

    @Query("SELECT f.currencyCode FROM Forex f")
    List<String> findAllCurrencyCodes();
}
