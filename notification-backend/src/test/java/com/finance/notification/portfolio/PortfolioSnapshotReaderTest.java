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

    @Test
    void findTodayAggregateForUsers_populatesResult_whenRowMapperInvokes() throws java.sql.SQLException {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getString(1)).thenReturn("user-1");
        org.mockito.Mockito.when(rs.getBigDecimal(2)).thenReturn(new java.math.BigDecimal("1000"));
        org.mockito.Mockito.when(rs.getBigDecimal(3)).thenReturn(new java.math.BigDecimal("100"));
        org.mockito.Mockito.when(rs.getInt(4)).thenReturn(2);
        org.mockito.Mockito.doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(2);
            mapper.mapRow(rs, 0);
            return null;
        }).when(jdbcTemplate).query(anyString(),
                any(PreparedStatementSetter.class), any(RowMapper.class));

        Map<String, PortfolioSnapshotReader.AggregatedSnapshot> result =
                reader.findTodayAggregateForUsers(List.of("user-1"));

        assertThat(result).containsKey("user-1");
        PortfolioSnapshotReader.AggregatedSnapshot snap = result.get("user-1");
        assertThat(snap.totalValue()).isEqualByComparingTo("1000");
        assertThat(snap.dailyPnl()).isEqualByComparingTo("100");
        assertThat(snap.portfolioCount()).isEqualTo(2);
    }

    @Test
    void findTodayAggregateForUsers_skipsRow_whenPortfolioCountZero() throws java.sql.SQLException {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getInt(4)).thenReturn(0);
        org.mockito.Mockito.doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(2);
            mapper.mapRow(rs, 0);
            return null;
        }).when(jdbcTemplate).query(anyString(),
                any(PreparedStatementSetter.class), any(RowMapper.class));

        Map<String, PortfolioSnapshotReader.AggregatedSnapshot> result =
                reader.findTodayAggregateForUsers(List.of("user-1"));

        assertThat(result).isEmpty();
    }

    @Test
    void aggregatedSnapshot_computesPercent_whenPreviousValuePositive() throws java.sql.SQLException {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getString(1)).thenReturn("user-1");
        org.mockito.Mockito.when(rs.getBigDecimal(2)).thenReturn(new java.math.BigDecimal("110"));
        org.mockito.Mockito.when(rs.getBigDecimal(3)).thenReturn(new java.math.BigDecimal("10"));
        org.mockito.Mockito.when(rs.getInt(4)).thenReturn(1);
        org.mockito.Mockito.doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(2);
            mapper.mapRow(rs, 0);
            return null;
        }).when(jdbcTemplate).query(anyString(),
                any(PreparedStatementSetter.class), any(RowMapper.class));

        Map<String, PortfolioSnapshotReader.AggregatedSnapshot> result =
                reader.findTodayAggregateForUsers(List.of("user-1"));

        assertThat(result.get("user-1").dailyPnlPercent()).isEqualByComparingTo("10");
    }

    @Test
    void aggregatedSnapshot_skipsPercent_whenPreviousValueZero() throws java.sql.SQLException {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getString(1)).thenReturn("user-1");
        org.mockito.Mockito.when(rs.getBigDecimal(2)).thenReturn(new java.math.BigDecimal("100"));
        org.mockito.Mockito.when(rs.getBigDecimal(3)).thenReturn(new java.math.BigDecimal("100"));
        org.mockito.Mockito.when(rs.getInt(4)).thenReturn(1);
        org.mockito.Mockito.doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(2);
            mapper.mapRow(rs, 0);
            return null;
        }).when(jdbcTemplate).query(anyString(),
                any(PreparedStatementSetter.class), any(RowMapper.class));

        Map<String, PortfolioSnapshotReader.AggregatedSnapshot> result =
                reader.findTodayAggregateForUsers(List.of("user-1"));

        assertThat(result.get("user-1").dailyPnlPercent()).isNull();
    }
}
