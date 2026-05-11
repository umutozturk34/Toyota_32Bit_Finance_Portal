package com.finance.notification.broadcast.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserPreferenceCacheRecipientDirectory implements RecipientDirectory {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Page<String> findUserSubs(Pageable pageable) {
        List<String> rows = jdbcTemplate.query(
                "SELECT user_sub FROM user_preferences ORDER BY user_sub LIMIT ? OFFSET ?",
                (rs, n) -> rs.getString(1),
                pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(rows, pageable, count());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences", Long.class);
        return total == null ? 0L : total;
    }
}
