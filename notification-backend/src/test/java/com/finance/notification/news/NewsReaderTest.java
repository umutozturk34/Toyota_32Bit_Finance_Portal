package com.finance.notification.news;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsReaderTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private NewsReader reader;

    @BeforeEach
    void setUp() {
        reader = new NewsReader(jdbcTemplate);
    }

    @Test
    void recentNews_empty_returnsZeroCount() {
        NewsReader.RecentNews empty = NewsReader.RecentNews.empty();

        assertThat(empty.totalCount()).isZero();
        assertThat(empty.categories()).isEmpty();
        assertThat(empty.sampleTitles()).isEmpty();
    }

    @Test
    void findRecent_returnsEmpty_whenNoSampleRows() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Timestamp.class), anyInt()))
                .thenReturn(List.of());

        NewsReader.RecentNews result = reader.findRecent();

        assertThat(result.totalCount()).isZero();
    }

    @Test
    void findRecent_returnsEmpty_whenDataAccessExceptionThrown() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Timestamp.class), anyInt()))
                .thenThrow(new QueryTimeoutException("timeout"));

        NewsReader.RecentNews result = reader.findRecent();

        assertThat(result.totalCount()).isZero();
    }
}
