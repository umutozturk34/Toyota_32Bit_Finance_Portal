package com.finance.notification.core.model;

import com.finance.notification.core.dto.NotificationPreferenceUpdateRequest;
import com.finance.notification.market.session.SessionMarket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-user notification preferences: a master email switch plus independent in-app/email toggles per
 * {@link NotificationType}, and the set of markets the user wants session open/close alerts for.
 * Email delivery additionally requires the master {@code emailEnabled} flag.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "notification_preferences")
@org.hibernate.annotations.DynamicUpdate
public class NotificationPreference {

    private static final String MARKETS_DELIMITER = ",";

    private static final EnumSet<SessionMarket> DEFAULT_OPT_OUT_MARKETS = EnumSet.of(SessionMarket.CRYPTO);

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "email_price_alerts", nullable = false)
    private boolean emailPriceAlerts;

    @Column(name = "inapp_price_alerts", nullable = false)
    private boolean inappPriceAlerts;

    @Column(name = "email_watchlist", nullable = false)
    private boolean emailWatchlist;

    @Column(name = "inapp_watchlist", nullable = false)
    private boolean inappWatchlist;

    @Column(name = "email_system", nullable = false)
    private boolean emailSystem;

    @Column(name = "inapp_system", nullable = false)
    private boolean inappSystem;

    @Column(name = "email_market_opened", nullable = false)
    private boolean emailMarketOpened;

    @Column(name = "inapp_market_opened", nullable = false)
    private boolean inappMarketOpened;

    @Column(name = "email_market_closed", nullable = false)
    private boolean emailMarketClosed;

    @Column(name = "inapp_market_closed", nullable = false)
    private boolean inappMarketClosed;

    @Column(name = "email_market_data_updated", nullable = false)
    private boolean emailMarketDataUpdated;

    @Column(name = "inapp_market_data_updated", nullable = false)
    private boolean inappMarketDataUpdated;

    @Column(name = "email_news_published", nullable = false)
    private boolean emailNewsPublished;

    @Column(name = "inapp_news_published", nullable = false)
    private boolean inappNewsPublished;

    @Column(name = "email_portfolio_updated", nullable = false)
    private boolean emailPortfolioUpdated;

    @Column(name = "inapp_portfolio_updated", nullable = false)
    private boolean inappPortfolioUpdated;

    @Column(name = "email_macro_indicators", nullable = false)
    private boolean emailMacroIndicators;

    @Column(name = "inapp_macro_indicators", nullable = false)
    private boolean inappMacroIndicators;

    @Column(name = "market_session_markets", nullable = false, length = 96)
    private String marketSessionMarkets;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Applies a partial update in place; only non-null request fields overwrite current values. */
    public void applyUpdate(NotificationPreferenceUpdateRequest request) {
        if (request == null) return;
        if (request.emailEnabled() != null) setEmailEnabled(request.emailEnabled());
        if (request.emailPriceAlerts() != null) setEmailPriceAlerts(request.emailPriceAlerts());
        if (request.inappPriceAlerts() != null) setInappPriceAlerts(request.inappPriceAlerts());
        if (request.emailWatchlist() != null) setEmailWatchlist(request.emailWatchlist());
        if (request.inappWatchlist() != null) setInappWatchlist(request.inappWatchlist());
        if (request.emailSystem() != null) setEmailSystem(request.emailSystem());
        if (request.inappSystem() != null) setInappSystem(request.inappSystem());
        if (request.emailMarketOpened() != null) setEmailMarketOpened(request.emailMarketOpened());
        if (request.inappMarketOpened() != null) setInappMarketOpened(request.inappMarketOpened());
        if (request.emailMarketClosed() != null) setEmailMarketClosed(request.emailMarketClosed());
        if (request.inappMarketClosed() != null) setInappMarketClosed(request.inappMarketClosed());
        if (request.emailMarketDataUpdated() != null) setEmailMarketDataUpdated(request.emailMarketDataUpdated());
        if (request.inappMarketDataUpdated() != null) setInappMarketDataUpdated(request.inappMarketDataUpdated());
        if (request.emailNewsPublished() != null) setEmailNewsPublished(request.emailNewsPublished());
        if (request.inappNewsPublished() != null) setInappNewsPublished(request.inappNewsPublished());
        if (request.emailPortfolioUpdated() != null) setEmailPortfolioUpdated(request.emailPortfolioUpdated());
        if (request.inappPortfolioUpdated() != null) setInappPortfolioUpdated(request.inappPortfolioUpdated());
        if (request.emailMacroIndicators() != null) setEmailMacroIndicators(request.emailMacroIndicators());
        if (request.inappMacroIndicators() != null) setInappMacroIndicators(request.inappMacroIndicators());
        if (request.marketSessionMarkets() != null) setMarketSessionMarkets(request.marketSessionMarkets());
    }

    public boolean wantsInApp(NotificationType type) {
        return type.isInAppWantedBy(this);
    }

    /** Email is wanted only when the master switch is on and the per-type email toggle is set. */
    public boolean wantsEmail(NotificationType type) {
        return emailEnabled && type.isEmailWantedBy(this);
    }

    private static String defaultMarketSessionMarkets() {
        return Arrays.stream(SessionMarket.values())
                .filter(m -> !DEFAULT_OPT_OUT_MARKETS.contains(m))
                .map(SessionMarket::name)
                .reduce((a, b) -> a + MARKETS_DELIMITER + b)
                .orElse("");
    }

    /** Sensible default preferences for a user with no persisted row (all in-app on, most email off). */
    public static NotificationPreference defaultsFor(String userSub) {
        return NotificationPreference.builder()
                .userSub(userSub)
                .emailEnabled(true)
                .emailPriceAlerts(true)
                .inappPriceAlerts(true)
                .emailWatchlist(false)
                .inappWatchlist(true)
                .emailSystem(false)
                .inappSystem(true)
                .emailMarketOpened(false)
                .inappMarketOpened(true)
                .emailMarketClosed(false)
                .inappMarketClosed(true)
                .emailMarketDataUpdated(false)
                .inappMarketDataUpdated(false)
                .emailNewsPublished(false)
                .inappNewsPublished(true)
                .emailPortfolioUpdated(false)
                .inappPortfolioUpdated(true)
                .emailMacroIndicators(false)
                .inappMacroIndicators(true)
                .marketSessionMarkets(defaultMarketSessionMarkets())
                .build();
    }
}
