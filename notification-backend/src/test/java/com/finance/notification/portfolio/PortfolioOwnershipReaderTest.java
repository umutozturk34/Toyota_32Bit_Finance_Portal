package com.finance.notification.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioOwnershipReaderTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private PortfolioOwnershipReader reader;

    @BeforeEach
    void setUp() {
        reader = new PortfolioOwnershipReader(jdbcTemplate);
    }

    @Test
    void should_returnFalse_when_portfolioIdIsNull() {
        boolean result = reader.isOwner(null, "user-1");

        assertThat(result).isFalse();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(Class.class), any(Object[].class));
    }

    @Test
    void should_returnFalse_when_userSubIsNull() {
        boolean result = reader.isOwner(1L, null);

        assertThat(result).isFalse();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(Class.class), any(Object[].class));
    }

    @Test
    void should_returnFalse_when_userSubIsBlank() {
        boolean result = reader.isOwner(1L, "   ");

        assertThat(result).isFalse();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(Class.class), any(Object[].class));
    }

    @Test
    void should_returnTrue_when_queryReturnsTrue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("user-1")))
                .thenReturn(Boolean.TRUE);

        boolean result = reader.isOwner(1L, "user-1");

        assertThat(result).isTrue();
    }

    @Test
    void should_returnFalse_when_queryReturnsFalse() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("user-1")))
                .thenReturn(Boolean.FALSE);

        boolean result = reader.isOwner(1L, "user-1");

        assertThat(result).isFalse();
    }

    @Test
    void should_returnFalse_when_queryReturnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("user-1")))
                .thenReturn(null);

        boolean result = reader.isOwner(1L, "user-1");

        assertThat(result).isFalse();
    }

    @Test
    void should_passPortfolioIdAndUserSubToJdbc_when_invoked() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(42L), eq("sub-x")))
                .thenReturn(Boolean.TRUE);

        reader.isOwner(42L, "sub-x");

        verify(jdbcTemplate).queryForObject(anyString(), eq(Boolean.class), eq(42L), eq("sub-x"));
    }
}
