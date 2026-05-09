import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import { Layers, TrendingUp, Bookmark, Newspaper, Check, Plus, ChevronRight } from 'lucide-react';
import { useWidgetDefinitions } from '../../../shared/hooks/useWidgetDefinitions';

/**
 * @typedef {Object} TrayTileSpec
 * @property {string} id
 * @property {string} kind
 * @property {string} label
 * @property {Object} config
 * @property {string} accent
 * @property {any} Icon
 * @property {number} w
 * @property {number} h
 */

const SINGLETON_TILE_BASES = [
  { id: 'tile-movers-stock', kind: 'MOVERS', label: 'Hisse · Yük/Düş', config: { market: 'STOCK' }, accent: '#10b981', Icon: TrendingUp },
  { id: 'tile-movers-crypto', kind: 'MOVERS', label: 'Kripto · Yük/Düş', config: { market: 'CRYPTO' }, accent: '#f59e0b', Icon: TrendingUp },
  { id: 'tile-movers-forex', kind: 'MOVERS', label: 'Döviz · Yük/Düş', config: { market: 'FOREX' }, accent: '#3b82f6', Icon: TrendingUp },
  { id: 'tile-movers-fund', kind: 'MOVERS', label: 'Fon · Yük/Düş', config: { market: 'FUND' }, accent: '#8b5cf6', Icon: TrendingUp },
  { id: 'tile-movers-commodity', kind: 'MOVERS', label: 'Emtia · Yük/Düş', config: { market: 'COMMODITY' }, accent: '#f97316', Icon: TrendingUp },
  { id: 'tile-news', kind: 'NEWS', label: 'Haberler', config: {}, accent: '#06b6d4', Icon: Newspaper },
];

const ASSET_CARDS_TILE_BASE = {
  id: 'tile-asset-cards',
  kind: 'ASSET_CARDS',
  label: 'Yeni Asset Kartı',
  config: {},
  accent: '#6366f1',
  Icon: Layers,
};

function withSize(base, byKind) {
  const def = byKind.get(base.kind);
  return def ? { ...base, w: def.defaults.w, h: def.defaults.h } : { ...base, w: 4, h: 6 };
}

function buildWatchlistTiles(watchlists, byKind) {
  if (!Array.isArray(watchlists) || watchlists.length === 0) return [];
  return watchlists.map((wl) => withSize({
    id: `tile-watchlist-${wl.id}`,
    kind: 'WATCHLIST',
    label: wl.name || `Takip ${wl.id}`,
    config: { watchlistId: wl.id },
    accent: '#ec4899',
    Icon: Bookmark,
  }, byKind));
}

function singletonUsed(sections, tile) {
  if (tile.kind === 'MOVERS') return sections.some((s) => s.kind === 'MOVERS' && s.config?.market === tile.config.market);
  if (tile.kind === 'WATCHLIST') return sections.some((s) => s.kind === 'WATCHLIST' && s.config?.watchlistId === tile.config.watchlistId);
  return sections.some((s) => s.kind === tile.kind);
}

/**
 * @typedef {Object} WidgetTrayProps
 * @property {Array<{kind: string, config?: Object}>} sections
 * @property {Array<{id: number, name: string}>} [watchlists]
 * @property {(tile: TrayTileSpec, anchorEl?: HTMLElement) => void} onAdd
 * @property {(tile: TrayTileSpec) => void} onDragStart
 * @property {() => void} onDragEnd
 */

/** @param {WidgetTrayProps} props */
export default function WidgetTray({ sections, watchlists = [], onAdd, onDragStart, onDragEnd }) {
  const [openTab, setOpenTab] = useState(null);
  const containerRef = useRef(null);
  const { byKind, limits } = useWidgetDefinitions();
  const maxWidgets = limits.maxWidgetsPerLayout;
  const maxAssetCards = limits.maxAssetCardWidgetsPerLayout;

  useEffect(() => {
    const click = (e) => {
      if (!containerRef.current?.contains(e.target)) setOpenTab(null);
    };
    document.addEventListener('mousedown', click);
    return () => document.removeEventListener('mousedown', click);
  }, []);

  const atCap = sections.length >= maxWidgets;
  const assetCardCount = sections.filter((s) => s.kind === 'ASSET_CARDS').length;
  const assetCardsFull = assetCardCount >= maxAssetCards;

  const singletonTiles = useMemo(() => SINGLETON_TILE_BASES.map((b) => withSize(b, byKind)), [byKind]);
  const assetCardsTile = useMemo(() => withSize(ASSET_CARDS_TILE_BASE, byKind), [byKind]);

  const widgetItems = singletonTiles.map((tile) => {
    const used = singletonUsed(sections, tile);
    return { tile, used, locked: !used && atCap };
  });
  const widgetUsedCount = widgetItems.filter((x) => x.used).length;

  const watchlistItems = buildWatchlistTiles(watchlists, byKind).map((tile) => {
    const used = singletonUsed(sections, tile);
    return { tile, used, locked: !used && atCap };
  });
  const watchlistUsedCount = watchlistItems.filter((x) => x.used).length;

  const toggle = (tab) => setOpenTab((p) => (p === tab ? null : tab));

  return (
    <div ref={containerRef} className="fixed left-3 top-[120px] z-30">
      <div className="flex flex-col gap-1.5 rounded-xl border border-accent/30 bg-bg-deep/90 backdrop-blur-md shadow-2xl shadow-black/40 p-1.5">
        <Tab
          active={openTab === 'widget'}
          accent="#6366f1"
          Icon={Layers}
          label="Widget Galerisi"
          count={`${widgetUsedCount}/${singletonTiles.length}`}
          onClick={() => toggle('widget')}
        />
        <Tab
          active={openTab === 'asset'}
          accent={assetCardsTile.accent}
          Icon={Plus}
          label="Asset Kartı"
          count={`${assetCardCount}/${maxAssetCards}`}
          onClick={() => toggle('asset')}
        />
        <Tab
          active={openTab === 'watchlist'}
          accent="#ec4899"
          Icon={Bookmark}
          label="Watchlist"
          count={watchlistItems.length === 0 ? '0' : `${watchlistUsedCount}/${watchlistItems.length}`}
          onClick={() => toggle('watchlist')}
          disabled={watchlistItems.length === 0}
        />
      </div>

      <AnimatePresence>
        {openTab === 'widget' && (
          <Dropdown key="d-widget" rowIndex={0}>
            <DropdownHint text={atCap ? `${maxWidgets}/${maxWidgets} doldu — silmeden ekleyemezsin` : 'Sürükle veya tıkla'} tone={atCap ? 'danger' : 'muted'} />
            <DropdownList>
              {widgetItems.map(({ tile, used, locked }) => (
                <DropdownTile key={tile.id} tile={tile} used={used} locked={locked} maxWidgets={maxWidgets} onAdd={(t, el) => { onAdd(t, el); setOpenTab(null); }} onDragStart={onDragStart} onDragEnd={onDragEnd} />
              ))}
            </DropdownList>
          </Dropdown>
        )}
        {openTab === 'asset' && (
          <Dropdown key="d-asset" rowIndex={1}>
            <DropdownHint text={`${assetCardCount} / ${maxAssetCards} eklendi`} tone={assetCardsFull ? 'danger' : 'muted'} />
            <DropdownList>
              <AssetCardAddRow tile={assetCardsTile} maxAssetCards={maxAssetCards} locked={assetCardsFull || atCap} assetCardCount={assetCardCount} onAdd={(t, el) => { onAdd(t, el); setOpenTab(null); }} onDragStart={onDragStart} onDragEnd={onDragEnd} />
            </DropdownList>
          </Dropdown>
        )}
        {openTab === 'watchlist' && watchlistItems.length > 0 && (
          <Dropdown key="d-watchlist" rowIndex={2}>
            <DropdownHint text={`${watchlistUsedCount} / ${watchlistItems.length} eklendi`} tone="muted" />
            <DropdownList>
              {watchlistItems.map(({ tile, used, locked }) => (
                <DropdownTile key={tile.id} tile={tile} used={used} locked={locked} maxWidgets={maxWidgets} onAdd={(t, el) => { onAdd(t, el); setOpenTab(null); }} onDragStart={onDragStart} onDragEnd={onDragEnd} />
              ))}
            </DropdownList>
          </Dropdown>
        )}
      </AnimatePresence>
    </div>
  );
}

/** @param {{active: boolean, accent: string, Icon: any, label: string, count?: string, disabled?: boolean, onClick: () => void}} props */
function Tab({ active, accent, Icon, label, count, disabled = false, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`flex items-center gap-2 rounded-lg border px-2.5 py-2 text-[12px] font-display font-semibold tracking-tight transition-all duration-150 cursor-pointer w-[200px] ${
        disabled
          ? 'border-border-default bg-bg-elevated/30 text-fg-subtle cursor-not-allowed opacity-50'
          : active
            ? 'shadow-md'
            : 'border-border-default bg-bg-elevated/60 text-fg-muted hover:text-fg hover:border-border-hover hover:bg-bg-elevated'
      }`}
      style={active && !disabled ? { borderColor: `${accent}80`, background: `${accent}1f`, color: accent } : undefined}
    >
      <span
        className="flex items-center justify-center w-7 h-7 rounded-md shrink-0"
        style={active && !disabled
          ? { background: `${accent}30`, color: accent }
          : disabled
            ? undefined
            : { background: `${accent}18`, color: accent }}
      >
        <Icon className="h-3.5 w-3.5" />
      </span>
      <span className="flex-1 text-left">{label}</span>
      {count && <span className="font-mono text-[10px] tabular-nums opacity-70">{count}</span>}
      <ChevronRight className={`h-3 w-3 transition-transform duration-150 ${active ? 'rotate-90' : ''}`} />
    </button>
  );
}

/** @param {{rowIndex: number, children: any}} props */
function Dropdown({ rowIndex, children }) {
  const tabHeight = 44;
  const gap = 6;
  const top = rowIndex * (tabHeight + gap);
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.12, ease: 'easeOut' }}
      style={{ top }}
      className="absolute left-full ml-2 z-50 min-w-[280px] max-w-[420px] rounded-xl border border-accent/30 bg-bg-deep/95 backdrop-blur-md shadow-2xl shadow-black/40 p-2 space-y-2"
    >
      {children}
    </motion.div>
  );
}

function DropdownHint({ text, tone }) {
  const cls = tone === 'danger' ? 'text-danger' : 'text-fg-subtle';
  return <div className={`font-mono text-[9px] tracking-wider px-1 ${cls}`}>{text}</div>;
}

function DropdownList({ children }) {
  return <div className="flex flex-col gap-1">{children}</div>;
}

function DropdownTile({ tile, used, locked, maxWidgets, onAdd, onDragStart, onDragEnd }) {
  const Icon = tile.Icon;
  const disabled = used || locked;
  const handleDragStart = (e) => {
    if (disabled) { e.preventDefault(); return; }
    e.dataTransfer.setData('application/x-widget-kind', tile.kind);
    e.dataTransfer.effectAllowed = 'copy';
    onDragStart(tile);
  };
  const handleClick = (e) => { if (!disabled) onAdd(tile, e.currentTarget); };
  const titleText = used ? 'Zaten eklenmiş' : locked ? `Limit ${maxWidgets} widget` : tile.label;
  return (
    <div
      draggable={!disabled}
      onDragStart={handleDragStart}
      onDragEnd={onDragEnd}
      onClick={handleClick}
      className={`droppable-element flex items-center gap-2 rounded-md border px-2.5 py-1.5 transition-all duration-150 ${
        disabled
          ? 'border-border-default/40 bg-bg-elevated/30 cursor-not-allowed opacity-50'
          : 'border-border-default bg-bg-elevated/60 cursor-grab active:cursor-grabbing hover:border-border-hover hover:bg-bg-elevated'
      }`}
      title={titleText}
    >
      <span
        className="flex items-center justify-center w-6 h-6 rounded shrink-0"
        style={{ background: disabled ? 'transparent' : `${tile.accent}20`, color: disabled ? 'var(--color-fg-subtle)' : tile.accent }}
      >
        {used ? <Check className="h-3 w-3" /> : <Icon className="h-3 w-3" />}
      </span>
      <span className={`font-display text-[12px] font-semibold flex-1 ${disabled ? 'text-fg-subtle' : 'text-fg'} ${used ? 'line-through' : ''}`}>
        {tile.label}
      </span>
    </div>
  );
}

function AssetCardAddRow({ tile, maxAssetCards, locked, assetCardCount, onAdd, onDragStart, onDragEnd }) {
  const accent = tile.accent;
  const nextLabel = `Asset Kartları ${assetCardCount + 1}`;
  const handleDragStart = (e) => {
    if (locked) { e.preventDefault(); return; }
    e.dataTransfer.setData('application/x-widget-kind', tile.kind);
    e.dataTransfer.effectAllowed = 'copy';
    onDragStart(tile);
  };
  const handleClick = (e) => { if (!locked) onAdd(tile, e.currentTarget); };
  return (
    <div
      draggable={!locked}
      onDragStart={handleDragStart}
      onDragEnd={onDragEnd}
      onClick={handleClick}
      className={`droppable-element flex items-center gap-2 rounded-md border-2 border-dashed px-2.5 py-1.5 transition-all duration-150 ${
        locked ? 'cursor-not-allowed opacity-30' : 'cursor-grab active:cursor-grabbing hover:bg-bg-elevated/60'
      }`}
      style={{ borderColor: locked ? 'var(--color-border-default)' : `${accent}66` }}
      title={locked ? `Maks ${maxAssetCards} kart paneli` : `Yeni panel ekle: ${nextLabel}`}
    >
      <span
        className="flex items-center justify-center w-6 h-6 rounded shrink-0"
        style={{ background: locked ? 'transparent' : `${accent}20`, color: locked ? 'var(--color-fg-subtle)' : accent }}
      >
        <Plus className="h-3 w-3" />
      </span>
      <div className="flex flex-col leading-none flex-1">
        <span className={`font-display text-[12px] font-semibold ${locked ? 'text-fg-subtle' : 'text-fg'}`}>
          + Yeni Asset Kartı
        </span>
        {!locked && (
          <span className="font-mono text-[9px] tracking-wider text-fg-subtle mt-0.5">
            sırada: {nextLabel}
          </span>
        )}
      </div>
    </div>
  );
}
