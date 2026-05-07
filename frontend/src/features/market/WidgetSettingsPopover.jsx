import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { X } from 'lucide-react';
import SearchSuggestions from '../../shared/components/SearchSuggestions';
import { useWatchlists } from '../../shared/hooks/useWatchlist';

const NEWS_CATEGORIES = ['KRIPTO', 'PARITE', 'HISSE', 'TAHVIL', 'MAKRO', 'GENEL_FINANS'];
const MAX_ASSET_CHIPS = 12;

function NewsConfig({ config, onChange }) {
  const selected = new Set(config?.categories || []);
  const toggle = (cat) => {
    const next = new Set(selected);
    if (next.has(cat)) next.delete(cat);
    else next.add(cat);
    onChange({ ...config, categories: [...next] });
  };
  return (
    <div className="space-y-2">
      <p className="font-mono text-[10px] tracking-[0.18em] uppercase text-fg-subtle">▸ Öncelik kategorileri</p>
      <div className="flex flex-wrap gap-1">
        {NEWS_CATEGORIES.map((cat) => {
          const active = selected.has(cat);
          return (
            <button
              key={cat}
              type="button"
              onClick={() => toggle(cat)}
              className={`text-[9px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded border transition-colors cursor-pointer
                ${active
                  ? 'border-accent bg-accent/15 text-accent'
                  : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/40 hover:text-accent'}`}
            >
              {cat}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function WatchlistConfig({ config, onChange }) {
  const { data: lists } = useWatchlists();
  const items = lists || [];
  if (items.length === 0) {
    return <p className="text-[10px] text-fg-subtle font-mono leading-relaxed">Önce <span className="text-accent">/watch</span> sayfasından bir liste oluştur.</p>;
  }
  return (
    <div className="space-y-1.5">
      <p className="font-mono text-[10px] tracking-[0.18em] uppercase text-fg-subtle">▸ Hangi liste</p>
      <div className="space-y-1">
        {items.map((l) => {
          const active = config?.watchlistId === l.id || (!config?.watchlistId && l.isDefault);
          return (
            <button
              key={l.id}
              type="button"
              onClick={() => onChange({ ...config, watchlistId: l.id })}
              className={`w-full flex items-center justify-between gap-2 px-2 py-1.5 rounded font-mono text-[11px] transition-colors cursor-pointer border ${
                active
                  ? 'border-accent bg-accent/15 text-accent'
                  : 'border-border-default bg-transparent text-fg-muted hover:border-accent/40 hover:text-fg'
              }`}
            >
              <span className="truncate">{l.name}</span>
              <span className="text-[9px] tabular-nums text-fg-subtle">{l.itemCount ?? 0}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function AssetCardsConfig({ config, onChange }) {
  const codes = Array.isArray(config?.assetCodes) ? config.assetCodes : [];
  const remove = (idx) => {
    const next = codes.filter((_, i) => i !== idx);
    onChange({ ...config, assetCodes: next });
  };
  const add = (asset) => {
    if (codes.length >= MAX_ASSET_CHIPS) return;
    if (codes.some((c) => c.code === asset.code && c.type === asset.type)) return;
    onChange({ ...config, assetCodes: [...codes, { type: asset.type, code: asset.code }] });
  };
  return (
    <div className="space-y-2">
      <p className="font-mono text-[10px] tracking-[0.18em] uppercase text-fg-subtle">▸ Sabitlediğin varlıklar</p>
      {codes.length === 0
        ? <p className="text-[10px] text-fg-subtle">Boş — aşağıdan asset ara ve ekle (varsayılan listesi yüklenir)</p>
        : <div className="flex flex-wrap gap-1">
            {codes.map((c, i) => (
              <span key={`${c.type}-${c.code}`} className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded border border-accent/40 bg-accent/10 text-accent">
                {c.code.replace('.IS', '')}
                <button type="button" onClick={() => remove(i)} className="hover:text-danger transition-colors bg-transparent border-none cursor-pointer p-0">
                  <X className="h-2.5 w-2.5" />
                </button>
              </span>
            ))}
          </div>
      }
      <div>
        <SearchSuggestions
          placeholder="BTC, AKBNK, USDTRY..."
          navigateOnSelect={false}
          onSelect={add}
        />
      </div>
      <p className="font-mono text-[9px] tracking-[0.16em] text-fg-subtle uppercase">{codes.length}/{MAX_ASSET_CHIPS} sabit varlık</p>
    </div>
  );
}

/**
 * @typedef {Object} WidgetSettingsPopoverProps
 * @property {string} sectionId
 * @property {string} kind
 * @property {Object} config
 * @property {(next: Object) => void} onChange
 * @property {() => void} onClose
 */

/** @param {WidgetSettingsPopoverProps} props */
export default function WidgetSettingsPopover({ kind, config, onChange, onClose }) {
  const ref = useRef(null);
  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onClose]);
  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: -4, scale: 0.96 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -4, scale: 0.96 }}
      transition={{ duration: 0.15, ease: [0.16, 1, 0.3, 1] }}
      className="absolute right-0 top-8 z-30 w-[280px] rounded-xl border border-accent/40 bg-bg-elevated shadow-2xl shadow-accent/10 backdrop-blur-md p-3 pointer-events-auto"
    >
      <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />
      {kind === 'NEWS' && <NewsConfig config={config} onChange={onChange} />}
      {kind === 'WATCHLIST' && <WatchlistConfig config={config} onChange={onChange} />}
      {kind === 'ASSET_CARDS' && <AssetCardsConfig config={config} onChange={onChange} />}
    </motion.div>
  );
}
