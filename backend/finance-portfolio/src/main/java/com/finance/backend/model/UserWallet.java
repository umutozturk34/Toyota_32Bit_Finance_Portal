package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_wallets",
        indexes = @Index(name = "idx_user_wallets_pf_currency",
                columnList = "portfolio_id, currency"))
public class UserWallet {

    private static final int SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "portfolio_id", insertable = false, updatable = false)
    private Long portfolioId;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public boolean hasSufficientBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount).setScale(SCALE, RoundingMode.HALF_UP);
        availableBalance = availableBalance.subtract(amount).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount).setScale(SCALE, RoundingMode.HALF_UP);
        availableBalance = availableBalance.add(amount).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
