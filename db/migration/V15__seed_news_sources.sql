
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (1, 'BloombergHT', 'https://www.bloomberght.com/rss', 'RSS', NULL, true, 0, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (2, 'Dunya-Finans', 'https://www.dunya.com/rss/finans.xml', 'RSS', NULL, true, 1, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (3, 'Dunya-Ekonomi', 'https://www.dunya.com/rss/ekonomi.xml', 'RSS', NULL, true, 2, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (4, 'ParaAnaliz', 'https://www.paraanaliz.com/feed', 'RSS', NULL, true, 3, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (5, 'FinansGundem-Kripto', 'https://finansgundem.com.tr/rss/kripto-piyasasi', 'RSS', 'CRYPTO', true, 4, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (6, 'FinansGundem-Borsa', 'https://finansgundem.com.tr/rss/borsa', 'RSS', 'BORSA_ISTANBUL', true, 5, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (7, 'FinansGundem-Emtia', 'https://finansgundem.com.tr/rss/emtia', 'RSS', 'EMTIA', true, 6, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (8, 'YatirimRehberi-Piyasalar', 'https://www.yatirimrehberi.com.tr/rss/piyasalar', 'RSS', 'GENEL_FINANS', true, 7, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (9, 'BorsaGundem-Sirket', 'https://www.borsagundem.com.tr/rss/sirket-haberleri', 'RSS', 'BORSA_SIRKETLERI', true, 8, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (10, 'Ekonomim-Para', 'https://www.ekonomim.net/rss/para-4', 'RSS', 'TAHVIL_BONO', true, 9, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (11, 'Ekonomim-Ekonomi', 'https://www.ekonomim.net/rss/ekonomi-5', 'RSS', NULL, true, 10, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;
INSERT INTO public.news_sources (id, name, url, source_type, default_category, enabled, sort_order, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (12, 'EkonomiDunya', 'https://www.ekonomidunya.com/rss_ekonomi_1.xml', 'RSS', 'PARITE', true, 11, '2026-05-25 07:46:52.447948', '2026-05-25 07:46:52.447948') ON CONFLICT DO NOTHING;

-- Broader general-finance + crypto coverage (the original crypto feed is Bitcoin-centric, so non-BTC coins were
-- rarely linked). Broad sources keep a NULL default_category so the per-article classifier decides; the crypto
-- source is hinted CRYPTO. ParaAnaliz (id 4) is disabled — its feed returns HTTP 404 and fails on every update.
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

SELECT pg_catalog.setval('public.news_sources_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM public.news_sources), 19));
