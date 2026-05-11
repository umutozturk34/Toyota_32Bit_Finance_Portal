package com.finance.notification.news;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewsReader {

    private static final int RECENT_WINDOW_MINUTES = 60;
    private static final int SAMPLE_TITLE_LIMIT = 3;

    private static final String COUNT_QUERY = """
            SELECT COUNT(*) FROM news_articles WHERE fetched_at >= ?
            """;

    private static final String SAMPLE_QUERY = """
            SELECT title, category
            FROM news_articles
            WHERE fetched_at >= ?
            ORDER BY fetched_at DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public record RecentNews(int totalCount, List<String> categories, List<String> sampleTitles) {
        public static RecentNews empty() {
            return new RecentNews(0, List.of(), List.of());
        }
    }

    @Transactional(readOnly = true)
    public RecentNews findRecent() {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusMinutes(RECENT_WINDOW_MINUTES));
        try {
            List<TitleCategory> rows = jdbcTemplate.query(
                    SAMPLE_QUERY,
                    (rs, n) -> new TitleCategory(rs.getString(1), rs.getString(2)),
                    cutoff, SAMPLE_TITLE_LIMIT);
            if (rows.isEmpty()) return RecentNews.empty();

            Integer total = jdbcTemplate.queryForObject(COUNT_QUERY, Integer.class, cutoff);
            Set<String> categorySet = new LinkedHashSet<>();
            List<String> titles = new ArrayList<>(rows.size());
            for (TitleCategory row : rows) {
                titles.add(row.title);
                if (row.category != null) categorySet.add(row.category);
            }
            return new RecentNews(
                    total == null ? rows.size() : total,
                    List.copyOf(categorySet),
                    Collections.unmodifiableList(titles));
        } catch (DataAccessException ex) {
            return RecentNews.empty();
        }
    }

    private record TitleCategory(String title, String category) {}
}
