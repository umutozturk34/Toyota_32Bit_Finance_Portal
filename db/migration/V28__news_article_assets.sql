-- =====================================================================================================
-- NEWS ↔ ASSET LINKAGE
-- =====================================================================================================
-- The market asset(s) each news article mentions, resolved server-side at ingest (parenthesised BIST ticker
-- OR company name, validated against the stock catalogue). Stored as an @ElementCollection on NewsArticle so
-- one article can reference several assets. Replaces the previous client-side, per-render matching: the link
-- now travels with the article (list/detail API, and a GET /news?assetCode=… filter).
-- Re-runnable and additive (existing articles are backfilled once on startup).
-- =====================================================================================================

CREATE TABLE IF NOT EXISTS news_article_assets (
    article_id BIGINT      NOT NULL REFERENCES news_articles (id) ON DELETE CASCADE,
    asset_code VARCHAR(32) NOT NULL,
    asset_type VARCHAR(20) NOT NULL DEFAULT 'STOCK',
    PRIMARY KEY (article_id, asset_code)
);

-- Drives the "news for asset X" lookup (GET /news?assetCode=THYAO.IS).
CREATE INDEX IF NOT EXISTS idx_news_article_assets_code ON news_article_assets (asset_code);
