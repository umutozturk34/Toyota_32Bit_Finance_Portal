package com.finance.news.service.source;

import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsArticleAsset;
import com.finance.news.port.AssetMentionResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sets an article's mentioned assets from the {@link AssetMentionResolver}, if one is on the classpath. Injected via
 * {@link ObjectProvider} so finance-news still runs (and tests pass) when no resolver implementation is present — the
 * resolver lives in finance-app, which can see the asset catalog. Used both at ingest and by the one-time backfill.
 *
 * <p>The FULL body is searched, not just the short description: a market wrap names "BIST 100" in its lead sentence
 * (the description) but lists every firm/ticker — Akbank (AKBNK), ASELSAN (ASELS), Türk Hava Yolları (THYAO) — deep
 * in the {@code content}, so matching only title+description would link the index and miss every stock.
 */
@Component
public class NewsAssetEnricher {

    private final ObjectProvider<AssetMentionResolver> resolverProvider;

    public NewsAssetEnricher(ObjectProvider<AssetMentionResolver> resolverProvider) {
        this.resolverProvider = resolverProvider;
    }

    /** Resolves and sets {@code article}'s assets; returns {@code true} when at least one asset was attached. */
    public boolean enrich(NewsArticle article) {
        AssetMentionResolver resolver = resolverProvider.getIfAvailable();
        if (resolver == null) {
            return false;
        }
        // Search the description AND the full content together (the resolver treats the second arg as one haystack),
        // so firms/tickers that only appear in the body are linked too — not just whatever the lead sentence names.
        String body = join(article.getDescription(), article.getContent());
        List<AssetMentionResolver.ResolvedAsset> refs = resolver.resolve(article.getTitle(), body);
        if (refs.isEmpty()) {
            return false;
        }
        Set<NewsArticleAsset> assets = refs.stream()
                .map(r -> new NewsArticleAsset(r.code(), r.type()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        article.setAssets(assets);
        return true;
    }

    /** Joins the non-blank parts with a space so the resolver searches them as one text (either part may be null). */
    private static String join(String description, String content) {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) {
            sb.append(description);
        }
        if (content != null && !content.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(content);
        }
        return sb.toString();
    }
}
