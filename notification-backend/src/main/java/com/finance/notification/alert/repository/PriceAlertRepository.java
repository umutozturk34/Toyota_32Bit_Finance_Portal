package com.finance.notification.alert.repository;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.PriceAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    Page<PriceAlert> findByUserSubOrderByCreatedAtDesc(String userSub, Pageable pageable);

    List<PriceAlert> findByActiveTrueAndMarketType(MarketType marketType);
}
