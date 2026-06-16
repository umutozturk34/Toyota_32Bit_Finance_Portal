package com.finance.news.service.source;

import com.finance.common.market.MarketDataReadiness;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsArticleAsset;
import com.finance.news.repository.NewsArticleRepository;
import com.finance.news.service.article.NewsCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-time enrichment of pre-existing articles that were ingested before the news↔asset link existed (e.g. the demo
 * seed). Runs once on startup in a background thread so readiness isn't blocked, walking unenriched articles by an
 * id cursor (so a no-mention article is visited once, never re-looped) and saving only those that gain a link. New
 * articles are enriched at ingest, so this is purely a catch-up for the existing corpus.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class NewsAssetBackfill {

    private static final int BATCH = 500;
    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_MS = 20_000;
    // Cold-start market-data load (stocks/cryptos + candles over external APIs) can take many minutes; poll its
    // readiness up to ~30 min so the catch-up scan runs against a populated catalog rather than an empty one.
    private static final int READINESS_MAX_CHECKS = 90;
    private static final long READINESS_CHECK_MS = 20_000;

    private final NewsArticleRepository articleRepository;
    private final NewsAssetEnricher assetEnricher;
    private final NewsCacheService newsCacheService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<MarketDataReadiness> marketDataReadiness;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread worker = new Thread(this::backfill, "news-asset-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    private void backfill() {
        // Wait for the market-data cold start to finish before resolving mentions: on a fresh `make up` the asset
        // catalog (stocks/cryptos) is still being fetched when the app reports ready, so an early scan would link
        // nothing but keywords and the resolver would have nothing to match firms/coins against. Bounded so a
        // stuck/failed init never hangs this daemon — after the cap we run a best-effort scan regardless.
        if (!awaitMarketData()) {
            log.warn("News asset backfill: market data not ready after waiting; running a best-effort scan anyway");
        }
        // The asset catalog (stocks/cryptos) may not be loaded the instant the app reports ready. If a full scan
        // links nothing yet articles still lack assets, the catalog probably isn't up — wait and retry, up to a cap.
        // Once a scan links something (catalog is up) or no article is left unlinked, we stop.
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
            try {
                int updated = runScan();
                if (updated > 0 || !articleRepository.existsWithoutAssets()) {
                    if (updated > 0) {
                        log.info("News asset backfill linked {} existing articles to assets", updated);
                    }
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("News asset backfill attempt {} failed: {}", attempt, e.getMessage());
            }
            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Blocks (in this daemon thread) until the market-data cold start reports ready, polling every
     * {@link #READINESS_CHECK_MS} up to {@link #READINESS_MAX_CHECKS} times. Returns true once ready; false if the
     * cap is hit first. When no readiness signal is on the classpath (finance-news standalone / tests) it returns
     * true immediately so enrichment is never blocked there.
     */
    private boolean awaitMarketData() {
        MarketDataReadiness readiness = marketDataReadiness.getIfAvailable();
        if (readiness == null) {
            return true;
        }
        for (int i = 0; i < READINESS_MAX_CHECKS; i += 1) {
            if (readiness.isReady()) {
                return true;
            }
            try {
                Thread.sleep(READINESS_CHECK_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return readiness.isReady();
    }

    /** One forward-cursor pass over every article; re-resolves and saves only those whose link set changed. */
    private int runScan() {
        long afterId = 0;
        int updated = 0;
        while (true) {
            List<NewsArticle> batch = articleRepository.findAllAfterId(afterId, PageRequest.of(0, BATCH));
            if (batch.isEmpty()) {
                break;
            }
            for (NewsArticle article : batch) {
                afterId = article.getId();
                // enrich() overwrites with the fresh resolution when non-empty and leaves the article untouched
                // when nothing matches (so a catalog that isn't loaded yet can never wipe existing links).
                Set<NewsArticleAsset> before = new LinkedHashSet<>(article.getAssets());
                // mentionCount is excluded from NewsArticleAsset.equals (the link identity is the asset code), so a
                // re-resolve that only changed the counts looks "unchanged". Track the counts separately so the
                // one-time catch-up also persists real mention counts onto articles linked before that feature.
                Map<String, Integer> beforeCounts = before.stream()
                        .collect(Collectors.toMap(NewsArticleAsset::getAssetCode, NewsArticleAsset::getMentionCount, (a, b) -> a));
                assetEnricher.enrich(article);
                boolean countsChanged = article.getAssets().stream()
                        .anyMatch(a -> !Integer.valueOf(a.getMentionCount()).equals(beforeCounts.get(a.getAssetCode())));
                if (!before.equals(article.getAssets()) || countsChanged) {
                    transactionTemplate.execute(status -> articleRepository.save(article));
                    updated++;
                }
                // Keep the read-through detail cache in sync: getById serves from Redis, so an article cached before
                // it gained links would otherwise keep showing no tags on the detail page until the TTL lapsed.
                // Re-cache every linked article (not just the freshly changed ones) so a stale pre-linkage copy from an
                // earlier run can't survive a restart either.
                if (!article.getAssets().isEmpty()) {
                    newsCacheService.cacheArticle(article);
                }
            }
        }
        return updated;
    }
}
