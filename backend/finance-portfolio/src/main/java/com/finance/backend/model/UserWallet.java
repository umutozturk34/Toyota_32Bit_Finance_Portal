package com.finance.backend.model;

import com.finance.backend.model.value.MoneyTRY;
import com.finance.backend.model.value.MoneyTRYConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_wallets",
        indexes = @Index(name = "idx_user_wallets_pf_currency",
                columnList = "portfolio_id, currency"))
public class UserWallet {

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

    @Convert(converter = MoneyTRYConverter.class)
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private MoneyTRY balance;

    @Convert(converter = MoneyTRYConverter.class)
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private MoneyTRY availableBalance;

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

    public boolean hasSufficientBalance(MoneyTRY amount) {
        return availableBalance.isGreaterThanOrEqualTo(amount);
    }

    public void debit(MoneyTRY amount) {
        balance = balance.minus(amount);
        availableBalance = availableBalance.minus(amount);
    }

    public void credit(MoneyTRY amount) {
        balance = balance.plus(amount);
        availableBalance = availableBalance.plus(amount);
    }
}
