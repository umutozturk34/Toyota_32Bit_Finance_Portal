package com.finance.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.backend.util.NewsCategoryResolver;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
                @Index(name = "idx_news_source", columnList = "source_name")
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

    @Column(name = "source_name", length = 100, nullable = false)
    private String sourceName;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

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

    public void resolveCategory(String defaultCategory) {
        this.category = NewsCategoryResolver.resolve(defaultCategory, this.title, this.description);
    }

    @JsonIgnore
    public boolean isStale(int maxAgeHours) {
        if (publishedAt == null) {
            return true;
        }
        return ChronoUnit.HOURS.between(publishedAt, LocalDateTime.now()) > maxAgeHours;
    }

    @JsonIgnore
    public long ageInHours() {
        if (publishedAt == null) {
            return -1;
        }
        return ChronoUnit.HOURS.between(publishedAt, LocalDateTime.now());
    }
}
