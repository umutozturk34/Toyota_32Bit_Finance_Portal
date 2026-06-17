-- Adds broader news sources for wider asset coverage and disables a dead feed.
--
-- The existing crypto feed (FinansGundem-Kripto) is Bitcoin-centric, so non-BTC coins (ETH/XRP/SOL/BNB…) were
-- almost never mentioned and thus never linked even though the catalog supports them. These additions broaden
-- both general-finance and crypto coverage; all were verified live (HTTP 200, fresh items). Broad sources are
-- left with a NULL default_category so the per-article classifier decides; the crypto source is hinted CRYPTO.
-- ParaAnaliz (id 4) is disabled — its feed returns HTTP 404 and fails on every update.

INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at)
OVERRIDING SYSTEM VALUE VALUES
  (13, 'TRTHaber-Ekonomi', 'https://www.trthaber.com/ekonomi_articles.rss', 'RSS', NULL, true, 12, now(), now()),
  (14, 'CNNTurk-Ekonomi', 'https://www.cnnturk.com/feed/rss/ekonomi/news', 'RSS', NULL, true, 13, now(), now()),
  (15, 'Foreks-ForInvest', 'https://www.foreks.com/rss', 'RSS', NULL, true, 14, now(), now()),
  (16, 'AA-Ekonomi', 'https://www.aa.com.tr/tr/rss/default?cat=ekonomi', 'RSS', NULL, true, 15, now(), now()),
  (17, 'Sabah-Ekonomi', 'https://www.sabah.com.tr/rss/ekonomi.xml', 'RSS', NULL, true, 16, now(), now()),
  (18, 'Haberturk-Ekonomi', 'https://www.haberturk.com/rss/ekonomi.xml', 'RSS', NULL, true, 17, now(), now()),
  (19, 'Bitcoinsistemi', 'https://www.bitcoinsistemi.com/feed/', 'RSS', 'CRYPTO', true, 18, now(), now())
ON CONFLICT DO NOTHING;

UPDATE public.news_sources SET enabled = false, updated_at = now() WHERE name = 'ParaAnaliz';

SELECT pg_catalog.setval('public.news_sources_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM public.news_sources), 19));
