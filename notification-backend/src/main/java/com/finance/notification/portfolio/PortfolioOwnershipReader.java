package com.finance.notification.portfolio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks whether a portfolio belongs to a user, reading the replicated portfolios table directly. */
@Service
@RequiredArgsConstructor
public class PortfolioOwnershipReader {

    private static final String OWNERSHIP_QUERY =
            "SELECT EXISTS(SELECT 1 FROM portfolios WHERE id = ? AND user_sub = ?)";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public boolean isOwner(Long portfolioId, String userSub) {
        if (portfolioId == null || userSub == null || userSub.isBlank()) return false;
        Boolean owns = jdbcTemplate.queryForObject(OWNERSHIP_QUERY, Boolean.class, portfolioId, userSub);
        return Boolean.TRUE.equals(owns);
    }
}
