package com.finance.notification.core.repository;

import com.finance.notification.core.model.NotificationPreference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    @Query("""
            SELECT p FROM NotificationPreference p
            WHERE p.inappPortfolioUpdated = true
               OR (p.emailEnabled = true AND p.emailPortfolioUpdated = true)
            """)
    Page<NotificationPreference> findPortfolioSubscribed(Pageable pageable);

    @Query("""
            SELECT p FROM NotificationPreference p
            WHERE p.inappNewsPublished = true
               OR (p.emailEnabled = true AND p.emailNewsPublished = true)
            """)
    Page<NotificationPreference> findNewsSubscribed(Pageable pageable);

    @Query("""
            SELECT p FROM NotificationPreference p
            WHERE (p.inappMarketDataUpdated = true
                   OR (p.emailEnabled = true AND p.emailMarketDataUpdated = true))
              AND p.marketSessionMarkets LIKE CONCAT('%', :market, '%')
            """)
    Page<NotificationPreference> findMarketDataSubscribed(String market, Pageable pageable);

    @Query("""
            SELECT p FROM NotificationPreference p
            WHERE (p.inappMarketOpened = true
                   OR (p.emailEnabled = true AND p.emailMarketOpened = true))
              AND p.marketSessionMarkets LIKE CONCAT('%', :market, '%')
            """)
    Page<NotificationPreference> findMarketOpenedSubscribed(String market, Pageable pageable);

    @Query("""
            SELECT p FROM NotificationPreference p
            WHERE (p.inappMarketClosed = true
                   OR (p.emailEnabled = true AND p.emailMarketClosed = true))
              AND p.marketSessionMarkets LIKE CONCAT('%', :market, '%')
            """)
    Page<NotificationPreference> findMarketClosedSubscribed(String market, Pageable pageable);
}
