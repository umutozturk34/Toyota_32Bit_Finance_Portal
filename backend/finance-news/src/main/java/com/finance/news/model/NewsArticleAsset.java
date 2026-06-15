package com.finance.news.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An asset an article was found to mention — the server-side news↔asset link. {@code assetCode} is the full market
 * code (e.g. {@code "AHGAZ.IS"}) so the UI can deep-link to the asset; {@code assetType} is its market type
 * ({@code "STOCK"} today). Stored as an {@code @ElementCollection} on {@link NewsArticle}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NewsArticleAsset {

    @Column(name = "asset_code", length = 32, nullable = false)
    private String assetCode;

    @Column(name = "asset_type", length = 20, nullable = false)
    private String assetType;
}
