package com.finance.notification.macro;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorChangeReaderTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private MacroIndicatorChangeReader reader;

    @BeforeEach
    void setUp() {
        reader = new MacroIndicatorChangeReader(jdbcTemplate);
    }

    @Test
    void should_returnEmpty_when_codesIsEmpty() {
        List<MacroIndicatorChangeReader.IndicatorChange> result = reader.findChanges(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void should_filterFlatRows_when_deltaIsZero() throws Exception {
        ResultSet flatRow = mockRow("TP.SAME", "Same", "INFLATION", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("40.00"),
                LocalDate.of(2026, 5, 19), new BigDecimal("40.00"));
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(inv -> {
                    RowMapper<MacroIndicatorChangeReader.IndicatorChange> mapper = inv.getArgument(2);
                    return List.of(mapper.mapRow(flatRow, 0));
                });

        List<MacroIndicatorChangeReader.IndicatorChange> result = reader.findChanges(List.of("TP.SAME"));

        assertThat(result).isEmpty();
    }

    @Test
    void should_keepRows_when_valueIncreased_andComputePositiveDelta() throws Exception {
        ResultSet upRow = mockRow("TP.RATE", "Rate", "RATES", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("40.50"),
                LocalDate.of(2026, 5, 19), new BigDecimal("40.00"));
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(inv -> {
                    RowMapper<MacroIndicatorChangeReader.IndicatorChange> mapper = inv.getArgument(2);
                    return List.of(mapper.mapRow(upRow, 0));
                });

        List<MacroIndicatorChangeReader.IndicatorChange> result = reader.findChanges(List.of("TP.RATE"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).direction()).isEqualTo(MacroIndicatorChangeReader.IndicatorChange.Direction.UP);
        assertThat(result.get(0).deltaAbsolute()).isEqualByComparingTo("0.50");
        assertThat(result.get(0).deltaPercent()).isNotNull();
        assertThat(result.get(0).deltaPercent().signum()).isPositive();
    }

    @Test
    void should_filterRowsWithoutPreviousValue_asTheyCannotBeCompared() throws Exception {
        ResultSet noPrevRow = mockRow("TP.NEW", "New", "RATES", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("40.00"),
                null, null);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(inv -> {
                    RowMapper<MacroIndicatorChangeReader.IndicatorChange> mapper = inv.getArgument(2);
                    return List.of(mapper.mapRow(noPrevRow, 0));
                });

        List<MacroIndicatorChangeReader.IndicatorChange> result = reader.findChanges(List.of("TP.NEW"));

        assertThat(result).isEmpty();
    }

    @Test
    void should_bindCodesArrayToBothQueryParameters_when_queryExecutes() throws Exception {
        ArgumentCaptor<PreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(PreparedStatementSetter.class);
        when(jdbcTemplate.query(anyString(), setterCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of());
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection connection = mock(Connection.class);
        Array array = mock(Array.class);
        when(ps.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"), any(Object[].class))).thenReturn(array);

        reader.findChanges(List.of("TP.RATE"));
        setterCaptor.getValue().setValues(ps);

        verify(connection).createArrayOf(eq("text"), any(Object[].class));
        verify(ps).setArray(1, array);
        verify(ps).setArray(2, array);
    }

    private static ResultSet mockRow(String code, String label, String category, String unit,
                                     String currency, String maturity,
                                     LocalDate currDate, BigDecimal currValue,
                                     LocalDate prevDate, BigDecimal prevValue) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("code")).thenReturn(code);
        when(rs.getString("label")).thenReturn(label);
        when(rs.getString("category")).thenReturn(category);
        when(rs.getString("unit")).thenReturn(unit);
        when(rs.getString("currency")).thenReturn(currency);
        when(rs.getString("maturity")).thenReturn(maturity);
        when(rs.getObject("curr_date", LocalDate.class)).thenReturn(currDate);
        when(rs.getBigDecimal("curr_value")).thenReturn(currValue);
        when(rs.getObject("prev_date", LocalDate.class)).thenReturn(prevDate);
        when(rs.getBigDecimal("prev_value")).thenReturn(prevValue);
        return rs;
    }
}
