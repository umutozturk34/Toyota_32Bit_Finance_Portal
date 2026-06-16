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
@EqualsAndHashCode(exclude = "mentionCount")
public class NewsArticleAsset {

    @Column(name = "asset_code", length = 32, nullable = false)
    private String assetCode;

    @Column(name = "asset_type", length = 20, nullable = false)
    private String assetType;

    /** How many times the article references this asset (ticker + name occurrences) — a prominence hint for the UI.
     *  Excluded from equality: the link's identity is the asset code, so the dedup Set keeps one row per asset. */
    @Column(name = "mention_count", nullable = false)
    private int mentionCount = 1;

    public NewsArticleAsset(String assetCode, String assetType) {
        this(assetCode, assetType, 1);
    }
}
