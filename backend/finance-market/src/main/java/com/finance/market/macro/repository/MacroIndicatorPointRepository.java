package com.finance.market.macro.repository;

import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MacroIndicatorPointRepository extends JpaRepository<MacroIndicatorPoint, Long> {

    Optional<MacroIndicatorPoint> findFirstByIndicatorOrderByObservedAtDesc(MacroIndicator indicator);

    List<MacroIndicatorPoint> findByIndicatorAndObservedAtBetweenOrderByObservedAtAsc(
            MacroIndicator indicator, LocalDate from, LocalDate to);

    boolean existsByIndicatorAndObservedAt(MacroIndicator indicator, LocalDate observedAt);
}
