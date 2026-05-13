package com.finance.notification.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotReaderTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private PortfolioSnapshotReader reader;

    @BeforeEach
    void setUp() {
        reader = new PortfolioSnapshotReader(jdbcTemplate);
    }

    @Test
    void findTodayAggregateForUsers_returnsEmpty_whenInputEmpty() {
        Map<String, PortfolioSnapshotReader.AggregatedSnapshot> result =
                reader.findTodayAggregateForUsers(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void findTodayAggregateForUsers_skipsNullAndBlankSubs() {
        Map<String, PortfolioSnapshotReader.AggregatedSnapshot> result =
                reader.findTodayAggregateForUsers(java.util.Arrays.asList(null, "   "));

        assertThat(result).isEmpty();
    }

    @Test
    void findTodayAggregateForUsers_invokesJdbcQuery_whenSubsPresent() {
        reader.findTodayAggregateForUsers(List.of("user-1", "user-2"));

        verify(jdbcTemplate).query(anyString(),
                any(PreparedStatementSetter.class), any(RowMapper.class));
    }
}
