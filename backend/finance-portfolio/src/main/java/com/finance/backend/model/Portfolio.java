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
@Table(name = "portfolios",
        indexes = @Index(name = "idx_portfolios_user_sub", columnList = "user_sub"))
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_sub", nullable = false)
    private String userSub;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "realized_pnl_try", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal realizedPnlTry = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (realizedPnlTry == null) realizedPnlTry = BigDecimal.ZERO;
    }

    public boolean isOwnedBy(String sub) {
        return userSub != null && userSub.equals(sub);
    }

    public void addRealizedPnl(BigDecimal pnl) {
        this.realizedPnlTry = this.realizedPnlTry.add(pnl).setScale(4, RoundingMode.HALF_UP);
    }
}
