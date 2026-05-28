package com.finance.portfolio.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A user-owned portfolio: the aggregate root that groups spot {@link PortfolioPosition} lots and
 * derivative positions. Holds no cash and no value of its own; all monetary figures are derived
 * from its positions and stored in TRY. Scoped to an owner via {@code userSub} (Keycloak subject).
 */
@Getter
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

    @Setter
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
