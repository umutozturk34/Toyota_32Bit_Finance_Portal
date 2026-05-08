package com.finance.news.model;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.news.util.NewsCategoryResolver;
import jakarta.persistence.*;
import lombok.*;

import com.finance.news.model.NewsSource;

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
                @Index(name = "idx_news_articles_source", columnList = "source_id")
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

    @JsonIgnore
    public String getSourceUrl() {
        return source != null ? source.getUrl() : null;
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
