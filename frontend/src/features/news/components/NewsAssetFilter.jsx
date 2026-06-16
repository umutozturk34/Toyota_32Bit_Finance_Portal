import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Tag, X, ChevronDown, Search } from 'lucide-react';
import { newsService } from '../services/newsService';

const bareCode = (code) => String(code || '').replace(/\.IS$/i, '');

// Collapsible ("burger") multi-select asset filter for the news page. Assets are GROUPED by category (Stock /
// Forex / Commodity / Crypto) so you can see what an asset is, and each chip shows BOTH its article count and
// its share % of all asset mentions (with a proportional bar), so the heavily-covered names stand out. Any
// number can be active at once — the page sends them comma-separated and the backend ORs them.
export default function NewsAssetFilter({ activeAssets, onToggle, onClear }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const { data: assets = [] } = useQuery({
    queryKey: ['newsAssetCounts'],
    queryFn: () => newsService.getAssetCounts(40),
    staleTime: 5 * 60 * 1000,
  });

  if (!Array.isArray(assets) || assets.length === 0) return null;
  const total = assets.reduce((s, a) => s + (Number(a.count) || 0), 0) || 1;
  const maxCount = Math.max(...assets.map((a) => Number(a.count) || 0), 1);
  const activeSet = new Set(activeAssets);
  const needle = query.trim().toUpperCase();
  const shown = needle ? assets.filter((a) => bareCode(a.code).toUpperCase().includes(needle)) : assets;

  // Group by market type, heaviest category first (search keeps % share based on the full set).
  const groupsMap = new Map();
  for (const a of shown) {
    const k = a.type || 'OTHER';
    if (!groupsMap.has(k)) groupsMap.set(k, []);
    groupsMap.get(k).push(a);
  }
  const groups = [...groupsMap.entries()]
    .map(([type, items]) => ({ type, items, sum: items.reduce((s, a) => s + Number(a.count), 0) }))
    .sort((a, b) => b.sum - a.sum);

  return (
    <div className="overflow-hidden rounded-xl border border-border-default bg-bg-elevated/40">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left transition-colors hover:bg-surface/40 cursor-pointer"
      >
        <Tag className="h-3.5 w-3.5 shrink-0 text-accent" />
        <span className="text-[11px] font-bold uppercase tracking-wider text-fg-subtle">{t('news.filterByAsset')}</span>
        {activeAssets.length > 0 && (
          <span className="rounded-full bg-accent/15 px-2 py-0.5 text-[10px] font-bold tabular-nums text-accent-bright">
            {t('news.assetsSelected', { count: activeAssets.length })}
          </span>
        )}
        {activeAssets.length > 0 && (
          <span
            role="button"
            tabIndex={0}
            onClick={(e) => { e.stopPropagation(); onClear(); }}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.stopPropagation(); onClear(); } }}
            className="inline-flex items-center gap-1 text-[11px] font-semibold text-accent hover:underline cursor-pointer"
          >
            <X className="h-3 w-3" /> {t('news.clearFilter')}
          </span>
        )}
        <ChevronDown className={`ml-auto h-4 w-4 shrink-0 text-fg-subtle transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: [0.32, 0.72, 0, 1] }}
            className="overflow-hidden"
          >
            <div className="space-y-3 border-t border-border-default/60 p-3">
              <div className="relative w-full sm:max-w-56">
                <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-fg-subtle" />
                <input
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  maxLength={24}
                  placeholder={t('news.assetSearch')}
                  className="w-full rounded-lg border border-border-default bg-bg-base/60 py-1.5 pl-8 pr-7 text-[11px] font-medium text-fg placeholder:text-fg-subtle focus:border-accent/50 focus:outline-none"
                />
                {query && (
                  <button type="button" onClick={() => setQuery('')} className="absolute right-1.5 top-1/2 -translate-y-1/2 p-0.5 text-fg-subtle hover:text-fg">
                    <X className="h-3 w-3" />
                  </button>
                )}
              </div>
              {groups.length === 0 && (
                <p className="py-4 text-center text-xs text-fg-muted">{t('news.noSearchResults')}</p>
              )}
              {groups.map((g) => (
                <div key={g.type} className="space-y-1.5">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-fg-subtle">
                    {t(`assets.labels.${g.type}`, { defaultValue: g.type })}
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {g.items.map((a) => {
                      const active = activeSet.has(a.code);
                      const share = (Number(a.count) / total) * 100;
                      const fillPct = Math.max(8, (Number(a.count) / maxCount) * 100);
                      return (
                        <button
                          key={`${a.type}:${a.code}`}
                          type="button"
                          onClick={() => onToggle(a.code)}
                          title={`${bareCode(a.code)} · %${share.toFixed(1)} · ${a.count}`}
                          className={`group relative inline-flex shrink-0 items-center gap-1.5 overflow-hidden rounded-lg border px-2.5 py-1.5 transition-colors duration-200 cursor-pointer ${
                            active ? 'border-accent/55 bg-accent/15' : 'border-border-default bg-bg-base/40 hover:border-accent/40'
                          }`}
                        >
                          <span aria-hidden className="absolute inset-y-0 left-0"
                            style={{ width: `${fillPct}%`, background: `linear-gradient(90deg, color-mix(in srgb, var(--color-accent) ${active ? 24 : 12}%, transparent), transparent)` }} />
                          <span className={`relative font-mono text-[11px] font-bold ${active ? 'text-accent-bright' : 'text-fg'}`}>{bareCode(a.code)}</span>
                          <span className={`relative font-mono text-[10px] font-bold tabular-nums ${active ? 'text-accent-bright' : 'text-fg-muted'}`}>%{share.toFixed(1)}</span>
                          <span className={`relative rounded-full px-1.5 text-[10px] font-bold tabular-nums ${active ? 'bg-accent/25 text-accent-bright' : 'bg-surface/70 text-fg-subtle'}`}>
                            {a.count}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
