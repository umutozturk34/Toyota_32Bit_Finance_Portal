package com.finance.notification.broadcast.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceCacheRecipientDirectoryTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private UserPreferenceCacheRecipientDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new UserPreferenceCacheRecipientDirectory(jdbcTemplate);
    }

    @Test
    void count_returnsRepositoryTotal() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        long total = directory.count();

        assertThat(total).isEqualTo(42L);
    }

    @Test
    void count_returnsZero_whenJdbcReturnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        long total = directory.count();

        assertThat(total).isZero();
    }

    @Test
    void findUserSubs_pagesResults_byQueryWithLimitAndOffset() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt(), anyLong()))
                .thenReturn(List.of("user-1", "user-2"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        Page<String> page = directory.findUserSubs(PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly("user-1", "user-2");
        assertThat(page.getTotalElements()).isEqualTo(2L);
    }
}
