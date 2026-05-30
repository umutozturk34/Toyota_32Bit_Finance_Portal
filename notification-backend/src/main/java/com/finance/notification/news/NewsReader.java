package com.finance.notification.news;

import com.finance.notification.config.NotificationDispatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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

/**
 * Reads a digest of articles published within the configured recent window: total count, distinct
 * categories and a few sample titles. Read failures degrade to an empty digest rather than failing
 * the fanout.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NewsReader {

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
    private final NotificationDispatchProperties dispatchProperties;

    /** Snapshot of recent news for the digest: total count plus distinct categories and sample titles. */
    public record RecentNews(int totalCount, List<String> categories, List<String> sampleTitles) {
        public static RecentNews empty() {
            return new RecentNews(0, List.of(), List.of());
        }
    }

    @Transactional(readOnly = true)
    public RecentNews findRecent() {
        NotificationDispatchProperties.NewsDigest cfg = dispatchProperties.newsDigest();
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusMinutes(cfg.recentWindowMinutes()));
        try {
            List<TitleCategory> rows = jdbcTemplate.query(
                    SAMPLE_QUERY,
                    (rs, n) -> new TitleCategory(rs.getString(1), rs.getString(2)),
                    cutoff, cfg.sampleTitleLimit());
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
            log.warn("News digest read failed cutoff={} cause={}", cutoff, ex.toString());
            return RecentNews.empty();
        }
    }

    private record TitleCategory(String title, String category) {}
}
