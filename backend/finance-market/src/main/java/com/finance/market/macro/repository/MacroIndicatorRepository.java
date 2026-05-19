package com.finance.market.macro.repository;

import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MacroIndicatorRepository extends JpaRepository<MacroIndicator, Long> {

    Optional<MacroIndicator> findByCode(String code);

    List<MacroIndicator> findByCategory(MacroCategory category);

    List<MacroIndicator> findByProminentTrueOrderByCategoryAsc();
}
