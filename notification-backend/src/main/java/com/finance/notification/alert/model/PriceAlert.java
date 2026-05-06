package com.finance.notification.alert.model;

import com.finance.common.model.MarketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "price_alerts")
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 16)
    private MarketType marketType;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private AlertDirection direction;

    @Column(name = "threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "reference_price", precision = 19, scale = 4)
    private BigDecimal referencePrice;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (currency == null || currency.isBlank()) currency = "TRY";
    }

    public boolean belongsTo(String candidateUserSub) {
        return userSub.equals(candidateUserSub);
    }

    public boolean shouldEvaluate() {
        return active && triggeredAt == null;
    }

    public boolean evaluate(BigDecimal currentPrice) {
        if (!shouldEvaluate() || currentPrice == null) {
            return false;
        }
        return direction.isFired(currentPrice, referencePrice, threshold);
    }

    public void markFired() {
        this.triggeredAt = LocalDateTime.now();
        this.active = false;
    }

    public void reactivate() {
        this.triggeredAt = null;
        this.active = true;
    }
}
