package com.finance.portfolio.model;

import com.finance.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A user-owned portfolio: the aggregate root that groups spot {@link PortfolioPosition} lots and
 * derivative positions. Holds no cash and no value of its own; all monetary figures are derived
 * from its positions and stored in TRY. Scoped to an owner via {@code userSub} (Keycloak subject).
 *
 * <p>Each portfolio is typed at creation ({@link PortfolioType}): a {@code SPOT} portfolio carries
 * only spot/VIOP positions, a {@code FIXED} one only deposit/bond holdings. The type is immutable
 * after creation (no setter) and gates which holdings may be added, so the two product lines never
 * co-mingle within a single portfolio.
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

    // No @Setter: the type is chosen once at creation and is immutable thereafter — changing it would orphan
    // the existing holdings (a SPOT portfolio's positions cannot be reinterpreted as fixed-income, and vice
    // versa). @Builder.Default backs the SPOT fallback so a builder that omits the type matches the column
    // default and the create-request's backward-compatible default.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PortfolioType type = PortfolioType.SPOT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (type == null) type = PortfolioType.SPOT;
    }

    /**
     * Asserts this portfolio is of {@code expected} type before a holding is added, throwing the type-specific
     * integrity error otherwise. The single domain home for the cross-type rule so every write path (spot lot,
     * VIOP open, deposit add, bond add) enforces it identically: a wrong-kind add is rejected with
     * {@code error.portfolio.notSpotType} when a SPOT portfolio is required, or {@code error.portfolio.notFixedType}
     * when a FIXED one is. Callers MUST run their userSub-ownership load first so an unowned portfolio still
     * surfaces as a 404 ahead of this check.
     */
    public void requireType(PortfolioType expected) {
        if (type != expected) {
            throw new BusinessException(expected == PortfolioType.SPOT
                    ? "error.portfolio.notSpotType"
                    : "error.portfolio.notFixedType");
        }
    }
}
