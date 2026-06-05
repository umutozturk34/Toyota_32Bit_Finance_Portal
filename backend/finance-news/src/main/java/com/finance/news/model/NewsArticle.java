package com.finance.news.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import com.finance.news.model.NewsSource;

import java.time.LocalDateTime;

/**
 * An ingested news article. The {@code link} is unique and acts as the natural dedup key alongside
 * {@code guid}; {@code category} is assigned by the classifier at ingest time.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "news_articles",
        indexes = {
                @Index(name = "idx_news_category", columnList = "category"),
                @Index(name = "idx_news_published_at", columnList = "published_at"),
                @Index(name = "idx_news_articles_source", columnList = "source_id"),
                @Index(name = "idx_news_articles_category_published", columnList = "category, published_at DESC")
        }
)
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @Column(name = "link", length = 1024, unique = true, nullable = false)
    private String link;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    @JsonIgnore
    private NewsSource source;

    @JsonIgnore
    public String getSourceName() {
        return source != null ? source.getName() : null;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private NewsCategory category;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "guid", length = 1024)
    private String guid;
}
