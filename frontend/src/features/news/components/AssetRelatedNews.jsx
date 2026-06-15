import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Newspaper, Calendar, ChevronRight } from 'lucide-react';
import { formatDateTimeShort } from '../../../shared/utils/formatters';
import { CategoryBadge } from '../lib/newsConfig.jsx';
import { getFallbackImage } from '../lib/newsConfig';
import { newsService } from '../services/newsService';
import Card from '../../../shared/components/card';

/**
 * One related-news card: image header (article image, with the same category-fallback the news grid uses) carrying
 * the relevance tag — the asset code when the article names the asset, else its market-category badge — over a
 * gradient, then source/title/date. Per-card image error state lives here so the map stays hook-free.
 */
function RelatedNewsCard({ article, mentions, codeTerm, onOpen }) {
  const fallback = getFallbackImage(article.category, article.id);
  const [imgSrc, setImgSrc] = useState(article.imageUrl || fallback);
  const [loaded, setLoaded] = useState(false);
  const handleError = () => {
    if (imgSrc !== fallback) {
      setImgSrc(fallback);
      setLoaded(false);
    } else {
      setLoaded(true);
    }
  };
  return (
    <Card
      as="button"
      type="button"
      onClick={onOpen}
      interactive
      radius="xl"
      padding="none"
      className="group flex flex-col overflow-hidden text-left w-full"
      aria-label={article.title}
    >
      <div className="relative h-28 w-full shrink-0 overflow-hidden bg-surface">
        <img
          src={imgSrc}
          alt={article.title}
          loading="lazy"
          className={`h-full w-full object-cover transition-all duration-500 group-hover:scale-105 ${loaded ? 'opacity-100' : 'opacity-0'}`}
          onLoad={() => setLoaded(true)}
          onError={handleError}
        />
        <div className="absolute inset-0 bg-gradient-to-t from-bg-elevated/85 via-transparent to-transparent" />
        <div className="absolute bottom-2 left-2.5 max-w-[calc(100%-1.25rem)]">
          {mentions ? (
            <span className="inline-flex items-center rounded-md border border-accent/30 bg-accent/20 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-accent backdrop-blur-sm truncate">
              {codeTerm}
            </span>
          ) : (
            <CategoryBadge category={article.category} />
          )}
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-1.5 p-3">
        <span className="truncate text-[10px] font-semibold uppercase tracking-widest text-accent">{article.sourceName}</span>
        <h3 className="text-[13px] font-semibold leading-snug text-fg line-clamp-2 break-words transition-colors group-hover:text-accent-bright">
          {article.title}
        </h3>
        <div className="flex-1" />
        <div className="flex items-center gap-1.5 text-[11px] text-fg-subtle">
          <Calendar size={11} strokeWidth={1.6} className="shrink-0" />
          <span className="truncate">{formatDateTimeShort(article.publishedAt)}</span>
        </div>
      </div>
    </Card>
  );
}

// Each asset type's "home" news category — the fallback tag shown when an article doesn't name the asset directly
// (a BIST stock's market news still belongs under Borsa İstanbul), and the source of the category-level backfill.
const TYPE_CATEGORY = {
  STOCK: 'BORSA_ISTANBUL',
  FUND: 'BORSA_ISTANBUL',
  VIOP: 'BORSA_ISTANBUL',
  CRYPTO: 'CRYPTO',
  FOREX: 'PARITE',
  COMMODITY: 'EMTIA',
  BOND: 'TAHVIL_BONO',
};

const MAX_ITEMS = 6;
const STALE_MS = 5 * 60 * 1000;

// Accent/case-insensitive normalize for the "does this article name the asset?" heuristic — mirrors the backend's
// unaccented search so "Türk Hava Yolları" matches "turk hava yollari" (Turkish ı/İ folded to i).
function normalize(text) {
  return (text || '')
    .toLocaleLowerCase('tr')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/ı/g, 'i');
}

/**
 * "İlgili Haberler" block for an asset detail page. There is no stored news↔asset link, so relatedness is derived:
 * articles that NAME the asset (by its code, then its display name — the backend full-text search is accent- and
 * case-insensitive) are the genuinely related ones, backfilled with the asset's market-category feed so the block
 * stays useful before anything names it. Each item is tagged with the asset code when its visible text mentions it,
 * otherwise with its market-category badge (the "Borsa İstanbul" fallback). Renders nothing when there is no news.
 */
export default function AssetRelatedNews({ assetCode, assetName, assetType }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const category = TYPE_CATEGORY[(assetType || '').toUpperCase()] || 'GENEL_FINANS';

  const codeTerm = (assetCode || '').trim();
  const nameTerm = (assetName || '').trim();

  const { data: byCode = [] } = useQuery({
    queryKey: ['relatedNews', 'code', codeTerm],
    queryFn: () => newsService.search({ search: codeTerm, size: MAX_ITEMS }).then((r) => r?.content ?? []),
    enabled: codeTerm.length >= 2,
    staleTime: STALE_MS,
  });
  const { data: byName = [] } = useQuery({
    queryKey: ['relatedNews', 'name', nameTerm],
    queryFn: () => newsService.search({ search: nameTerm, size: MAX_ITEMS }).then((r) => r?.content ?? []),
    enabled: nameTerm.length >= 4 && normalize(nameTerm) !== normalize(codeTerm),
    staleTime: STALE_MS,
  });
  const { data: byCategory = [] } = useQuery({
    queryKey: ['relatedNews', 'category', category],
    queryFn: () => newsService.search({ category, size: MAX_ITEMS }).then((r) => r?.content ?? []),
    staleTime: STALE_MS,
  });

  // Direct mentions first (code, then name), category feed as backfill; deduped by id, capped.
  const items = useMemo(() => {
    const seen = new Set();
    const out = [];
    for (const arr of [byCode, byName, byCategory]) {
      for (const a of arr) {
        if (!a?.id || seen.has(a.id)) continue;
        seen.add(a.id);
        out.push(a);
      }
    }
    return out.slice(0, MAX_ITEMS);
  }, [byCode, byName, byCategory]);

  const nCode = normalize(codeTerm);
  const nName = normalize(nameTerm);
  const mentionsAsset = (a) => {
    const hay = normalize(`${a?.title ?? ''} ${a?.description ?? ''}`);
    return (nCode.length >= 2 && hay.includes(nCode)) || (nName.length >= 4 && hay.includes(nName));
  };

  if (items.length === 0) return null;

  return (
    <motion.section
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      className="space-y-3"
      aria-label={t('news.related.title')}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-accent/10">
            <Newspaper className="h-4 w-4 text-accent" />
          </span>
          <h2 className="text-sm font-bold text-fg truncate">{t('news.related.title')}</h2>
        </div>
        <button
          type="button"
          onClick={() => navigate(`/news?search=${encodeURIComponent(codeTerm || nameTerm)}`)}
          className="inline-flex items-center gap-1 text-[11px] font-semibold text-fg-muted hover:text-accent transition-colors bg-transparent border-none cursor-pointer shrink-0"
        >
          {t('news.related.viewAll')}
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {items.map((a) => (
          <RelatedNewsCard
            key={a.id}
            article={a}
            mentions={mentionsAsset(a)}
            codeTerm={codeTerm}
            onOpen={() => navigate(`/news/${a.id}`)}
          />
        ))}
      </div>
    </motion.section>
  );
}
