package com.finance.news.service.source;

import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsArticleAsset;
import com.finance.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private final NewsArticleRepository articleRepository;
    private final NewsAssetEnricher assetEnricher;
    private final TransactionTemplate transactionTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread worker = new Thread(this::backfill, "news-asset-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    private void backfill() {
        try {
            long afterId = 0;
            int updated = 0;
            while (true) {
                List<NewsArticle> batch = articleRepository.findAllAfterId(afterId, PageRequest.of(0, BATCH));
                if (batch.isEmpty()) {
                    break;
                }
                for (NewsArticle article : batch) {
                    afterId = article.getId();
                    // Re-resolve against the current matcher and persist ONLY when the link set actually changed,
                    // so re-scanning every article on each startup stays cheap. enrich() overwrites with the fresh
                    // resolution when non-empty and leaves the article untouched when nothing matches (so a catalog
                    // that isn't loaded yet can never wipe existing links).
                    Set<NewsArticleAsset> before = new LinkedHashSet<>(article.getAssets());
                    assetEnricher.enrich(article);
                    if (!before.equals(article.getAssets())) {
                        transactionTemplate.execute(status -> articleRepository.save(article));
                        updated++;
                    }
                }
            }
            if (updated > 0) {
                log.info("News asset backfill linked {} existing articles to assets", updated);
            }
        } catch (RuntimeException e) {
            log.warn("News asset backfill skipped: {}", e.getMessage());
        }
    }
}
