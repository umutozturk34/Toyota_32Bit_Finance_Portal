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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsReaderTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private NewsReader reader;

    @BeforeEach
    void setUp() {
        com.finance.notification.config.NotificationDispatchProperties dispatchProperties =
                new com.finance.notification.config.NotificationDispatchProperties(null, null, null, null);
        reader = new NewsReader(jdbcTemplate, dispatchProperties);
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

    @Test
    void findRecent_returnsAggregatedTitlesAndCategories_whenRowsPresent() throws Exception {
        java.lang.reflect.Constructor<?> ctor = Class.forName(
                "com.finance.notification.news.NewsReader$TitleCategory")
                .getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object row1 = ctor.newInstance("BIST yükseldi", "STOCK");
        Object row2 = ctor.newInstance("Bitcoin sabit", "CRYPTO");
        Object row3 = ctor.newInstance("Genel başlık", null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Timestamp.class), anyInt()))
                .thenReturn(List.of(row1, row2, row3));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Timestamp.class)))
                .thenReturn(42);

        NewsReader.RecentNews result = reader.findRecent();

        assertThat(result.totalCount()).isEqualTo(42);
        assertThat(result.categories()).containsExactly("STOCK", "CRYPTO");
        assertThat(result.sampleTitles()).hasSize(3);
    }

    @Test
    void findRecent_fallsBackToRowSize_whenCountQueryReturnsNull() throws Exception {
        java.lang.reflect.Constructor<?> ctor = Class.forName(
                "com.finance.notification.news.NewsReader$TitleCategory")
                .getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object row1 = ctor.newInstance("title", "WORLD");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Timestamp.class), anyInt()))
                .thenReturn(List.of(row1));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Timestamp.class)))
                .thenReturn(null);

        NewsReader.RecentNews result = reader.findRecent();

        assertThat(result.totalCount()).isEqualTo(1);
    }
}
