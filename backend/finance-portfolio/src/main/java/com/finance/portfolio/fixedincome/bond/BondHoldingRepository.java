package com.finance.portfolio.fixedincome.bond;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bond-holding persistence. Every read is scoped by {@code portfolioId}; callers MUST first verify the
 * portfolio is owned by the authenticated user ({@code findByIdAndUserSub}) before touching any holding —
 * there is no findById-only mutation path, so ownership can never be bypassed.
 */
public interface BondHoldingRepository extends JpaRepository<BondHolding, Long> {

    // Explicit, deterministic ordering so the grid keeps a stable row order across mutations: a JPA derived
    // query without an OrderBy clause leaves ordering to the DB and can reshuffle after an add/edit/delete,
    // making a deleted row appear to "jump". id DESC is the tie-breaker for same-day entries.
    List<BondHolding> findByPortfolioIdOrderByEntryDateDescIdDesc(Long portfolioId);

    List<BondHolding> findByPortfolioIdAndExitDateIsNull(Long portfolioId);

    @Transactional
    void deleteByPortfolioId(Long portfolioId);
}
