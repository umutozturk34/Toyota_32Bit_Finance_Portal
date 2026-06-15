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
        List<AssetMentionResolver.ResolvedAsset> refs = resolver.resolve(article.getTitle(), article.getDescription());
        if (refs.isEmpty()) {
            return false;
        }
        Set<NewsArticleAsset> assets = refs.stream()
                .map(r -> new NewsArticleAsset(r.code(), r.type()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        article.setAssets(assets);
        return true;
    }
}
