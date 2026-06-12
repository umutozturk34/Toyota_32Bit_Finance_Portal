package com.finance.market.forex.repository;
import com.finance.market.forex.model.Forex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Persistence for forex identity/snapshot rows, keyed by currency code. */
public interface ForexRepository extends JpaRepository<Forex, String>, JpaSpecificationExecutor<Forex> {
    List<Forex> findAllByOrderByCurrencyCodeAsc();

    @Query("SELECT f.currencyCode FROM Forex f")
    List<String> findAllCurrencyCodes();
}
