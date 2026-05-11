import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { createPortal } from 'react-dom';
import { X, Search, Layers, Newspaper, Bookmark, GripVertical, Check } from 'lucide-react';
import {
  DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core';
import {
  arrayMove, SortableContext, useSortable, verticalListSortingStrategy, sortableKeyboardCoordinates,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
import { useWatchlists } from '../../../shared/hooks/useWatchlist';

const NEWS_CATEGORY_VALUES = ['CRYPTO', 'BORSA_ISTANBUL', 'BORSA_SIRKETLERI', 'TAHVIL_BONO', 'PARITE', 'EMTIA', 'GENEL_FINANS'];
const POPOVER_WIDTH = 340;
const POPOVER_MAX_HEIGHT = 460;
const GAP = 10;
const MAX_ASSET_CHIPS = 12;

function PopoverHeader({ Icon, title }) {
  return (
    <div className="flex items-center gap-2 mb-3">
      <span className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/15 border border-accent/25">
        <Icon className="h-3 w-3 text-accent" />
      </span>
      <span className="font-display text-[12px] font-bold text-fg tracking-tight">{title}</span>
    </div>
  );
}

function NewsConfig({ config, onChange }) {
  const { t } = useTranslation();
  const selected = new Set(config?.categories || []);
  const ordered = Array.isArray(config?.categories) ? config.categories : [];
  const toggle = (value) => {
    if (selected.has(value)) {
      onChange({ ...config, categories: ordered.filter((c) => c !== value) });
    } else {
      onChange({ ...config, categories: [...ordered, value] });
    }
  };
  return (
    <>
      <PopoverHeader Icon={Newspaper} title={t('widgetSettings.newsHeader')} />
      <p className="font-mono text-[9px] tracking-[0.16em] uppercase text-fg-subtle mb-2">{t('widgetSettings.priorityHint')}</p>
      <div className="flex flex-wrap gap-1.5">
        {NEWS_CATEGORY_VALUES.map((value) => {
          const active = selected.has(value);
          const rank = active ? ordered.indexOf(value) + 1 : null;
          return (
            <button
              key={value}
              type="button"
              onClick={() => toggle(value)}
              className={`relative font-display text-[11px] tracking-tight font-semibold px-2.5 py-1 rounded-md border transition-all cursor-pointer
                ${active
                  ? 'border-accent bg-accent/15 text-accent shadow-[inset_0_0_10px_-3px_var(--color-accent)]'
                  : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5'}`}
            >
              {active && <span className="absolute -top-1.5 -left-1.5 w-4 h-4 rounded-full bg-accent text-bg-deep text-[9px] font-mono font-bold flex items-center justify-center">{rank}</span>}
              {t(`widgetSettings.newsCategory.${value}`)}
            </button>
          );
        })}
      </div>
      <p className="font-mono text-[9px] tracking-[0.14em] text-fg-subtle uppercase mt-3 leading-relaxed">
        {t('widgetSettings.allCategoriesHint')}
      </p>
    </>
  );
}

function WatchlistConfig({ config, onChange }) {
  const { t } = useTranslation();
  const { data: lists } = useWatchlists();
  const items = lists || [];
  return (
    <>
      <PopoverHeader Icon={Bookmark} title={t('widgetSettings.watchlistHeader')} />
      {items.length === 0
        ? <p className="text-[11px] text-fg-subtle leading-relaxed">
            <span dangerouslySetInnerHTML={{ __html: t('widgetSettings.createListHint') }} />
          </p>
        : <div className="space-y-1.5">
            {items.map((l) => {
              const active = config?.watchlistId === l.id || (!config?.watchlistId && l.isDefault);
              return (
                <button
                  key={l.id}
                  type="button"
                  onClick={() => onChange({ ...config, watchlistId: l.id })}
                  className={`w-full flex items-center justify-between gap-2 px-2.5 py-2 rounded-lg font-display text-[12px] font-semibold transition-all cursor-pointer border
                    ${active
                      ? 'border-accent bg-accent/15 text-accent'
                      : 'border-border-default bg-transparent text-fg-muted hover:border-accent/40 hover:text-fg hover:bg-surface/50'}`}
                >
                  <span className="truncate">{l.name}</span>
                  <span className="font-mono text-[10px] tabular-nums text-fg-subtle">{l.itemCount ?? 0}</span>
                </button>
              );
            })}
          </div>}
    </>
  );
}

function SortableChip({ id, code, type, onRemove }) {
  const { t } = useTranslation();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };
  return (
    <span
      ref={setNodeRef}
      style={style}
      className="inline-flex items-center gap-1 font-mono text-[11px] uppercase tracking-[0.06em] font-semibold pl-1 pr-1.5 py-1 rounded-md border border-accent/40 bg-accent/10 text-accent select-none"
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        className="flex items-center hover:text-fg transition-colors bg-transparent border-none cursor-grab active:cursor-grabbing p-0"
        aria-label={t('widgetSettings.drag')}
      >
        <GripVertical className="h-3 w-3 opacity-60" />
      </button>
      <span>{code.replace('.IS', '')}</span>
      <span className="text-[8px] text-accent/60 ml-0.5 normal-case tracking-wider">{type}</span>
      <button
        type="button"
        onClick={onRemove}
        className="flex items-center hover:text-danger transition-colors bg-transparent border-none cursor-pointer p-0 ml-0.5"
        aria-label={t('widgetSettings.removeChip', { code })}
      >
        <X className="h-2.5 w-2.5" />
      </button>
    </span>
  );
}

function AssetCardsConfig({ config, onChange, autoFocusName }) {
  const { t } = useTranslation();
  const codes = Array.isArray(config?.assetCodes) ? config.assetCodes : [];
  const name = typeof config?.name === 'string' ? config.name : '';
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const ids = codes.map((c) => `${c.type}-${c.code}`);
  const remove = (idx) => onChange({ ...config, assetCodes: codes.filter((_, i) => i !== idx) });
  const add = (asset) => {
    if (codes.length >= MAX_ASSET_CHIPS) return;
    if (codes.some((c) => c.code === asset.code && c.type === asset.type)) return;
    onChange({ ...config, assetCodes: [...codes, asset] });
  };
  const handleDragEnd = (event) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = ids.indexOf(active.id);
    const newIndex = ids.indexOf(over.id);
    onChange({ ...config, assetCodes: arrayMove(codes, oldIndex, newIndex) });
  };
  const full = codes.length >= MAX_ASSET_CHIPS;
  return (
    <div className="flex flex-col h-full min-h-0">
      <PopoverHeader Icon={Layers} title={t('widgetSettings.assetCardHeader')} />
      <div className="shrink-0 mb-3">
        <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1">{t('widgetSettings.panelNameLabel')}</label>
        <input
          type="text"
          value={name}
          onChange={(e) => onChange({ ...config, name: e.target.value })}
          placeholder={t('widgetSettings.panelNamePlaceholder')}
          maxLength={40}
          autoFocus={autoFocusName}
          className="w-full font-display text-[13px] font-semibold px-2.5 py-1.5 rounded-md border border-border-default bg-bg-base/60 text-fg placeholder:text-fg-subtle focus:border-accent focus:bg-bg-base focus:outline-none focus:ring-2 focus:ring-accent/20 transition-all"
        />
      </div>
      <div className="shrink-0 flex items-center justify-between mb-1.5">
        <span className="font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle">{t('widgetSettings.pinnedAssets')}</span>
        <span className={`font-mono text-[9px] tabular-nums ${full ? 'text-warning' : 'text-fg-subtle'}`}>{codes.length}/{MAX_ASSET_CHIPS}</span>
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto pr-1 -mr-1 mb-2 scrollbar-auto-hide">
        {codes.length === 0
          ? <p className="text-[11px] text-fg-subtle leading-relaxed">
              {t('widgetSettings.emptyHint')}
            </p>
          : <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
              <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                <div className="flex flex-wrap gap-1.5">
                  {codes.map((c, i) => (
                    <SortableChip
                      key={`${c.type}-${c.code}`}
                      id={`${c.type}-${c.code}`}
                      code={c.code}
                      type={c.type}
                      onRemove={() => remove(i)}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>}
      </div>
      <div className="shrink-0 space-y-1.5">
        <div className={`rounded-lg border bg-bg-base/50 px-2 py-1.5 transition-opacity ${full ? 'opacity-50 pointer-events-none border-border-default' : 'border-border-default'}`}>
          <div className="flex items-center gap-1.5 mb-1.5 px-1">
            <Search className="h-3 w-3 text-fg-subtle" />
            <span className="font-mono text-[9px] tracking-[0.16em] uppercase text-fg-subtle">{t('widgetSettings.searchHint')}</span>
          </div>
          <SearchSuggestions placeholder={t('widgetSettings.searchPlaceholder')} navigateOnSelect={false} onSelect={add} />
        </div>
        {full && (
          <p className="font-mono text-[9px] tracking-[0.14em] text-warning uppercase">{t('widgetSettings.maxAssets')}</p>
        )}
      </div>
    </div>
  );
}

/** Computes a fixed-position rect that places the popover near `anchor` with smart fallbacks. */
function placeNear(anchor) {
  if (!anchor || typeof anchor.getBoundingClientRect !== 'function') {
    return { top: 80, left: 16, maxHeight: POPOVER_MAX_HEIGHT };
  }
  const rect = anchor.getBoundingClientRect();
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const rightSpace = vw - rect.right - GAP;
  const leftSpace = rect.left - GAP;
  const belowSpace = vh - rect.bottom - GAP;
  const aboveSpace = rect.top - GAP;
  let top;
  let left;
  let maxHeight;
  if (leftSpace >= POPOVER_WIDTH) {
    left = rect.left - POPOVER_WIDTH - GAP;
    top = Math.max(8, Math.min(rect.top, vh - POPOVER_MAX_HEIGHT - 8));
    maxHeight = Math.min(POPOVER_MAX_HEIGHT, vh - 16);
  } else if (rightSpace >= POPOVER_WIDTH) {
    left = rect.right + GAP;
    top = Math.max(8, Math.min(rect.top, vh - POPOVER_MAX_HEIGHT - 8));
    maxHeight = Math.min(POPOVER_MAX_HEIGHT, vh - 16);
  } else {
    left = Math.max(8, Math.min(rect.left, vw - POPOVER_WIDTH - 8));
    if (belowSpace >= aboveSpace) {
      top = rect.bottom + GAP;
      maxHeight = Math.min(POPOVER_MAX_HEIGHT, belowSpace);
    } else {
      maxHeight = Math.min(POPOVER_MAX_HEIGHT, aboveSpace);
      top = rect.top - maxHeight - GAP;
    }
  }
  return { top, left, maxHeight };
}

/**
 * @typedef {Object} WidgetSettingsPopoverProps
 * @property {HTMLElement|null} anchorEl
 * @property {string} kind
 * @property {Object} config
 * @property {boolean} [autoFocusName]
 * @property {(next: Object) => void} onChange
 * @property {() => void} onClose
 */

/** @param {WidgetSettingsPopoverProps} props */
export default function WidgetSettingsPopover({ anchorEl, kind, config, autoFocusName = false, onChange, onClose }) {
  const { t } = useTranslation();
  const ref = useRef(null);
  const [pos, setPos] = useState(null);

  useLayoutEffect(() => {
    const recompute = () => setPos(placeNear(anchorEl));
    recompute();
    window.addEventListener('resize', recompute);
    window.addEventListener('scroll', recompute, true);
    return () => {
      window.removeEventListener('resize', recompute);
      window.removeEventListener('scroll', recompute, true);
    };
  }, [anchorEl]);

  useEffect(() => {
    const clickHandler = (e) => {
      if (ref.current?.contains(e.target)) return;
      if (anchorEl && anchorEl.contains(e.target)) return;
      onClose();
    };
    const escHandler = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('mousedown', clickHandler);
    document.addEventListener('keydown', escHandler);
    return () => {
      document.removeEventListener('mousedown', clickHandler);
      document.removeEventListener('keydown', escHandler);
    };
  }, [anchorEl, onClose]);

  if (!pos) return null;

  return createPortal(
    <motion.div
      ref={ref}
      initial={{ opacity: 0, x: -16 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -16 }}
      transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
      style={{
        position: 'fixed',
        top: pos.top,
        left: pos.left,
        width: `min(${POPOVER_WIDTH}px, calc(100vw - 16px))`,
        maxHeight: pos.maxHeight,
        zIndex: 100,
      }}
      className="flex flex-col rounded-xl border border-accent/50 bg-bg-deep shadow-2xl shadow-black/60"
    >
      <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/60 to-transparent rounded-t-xl" />
      <button
        type="button"
        onClick={onClose}
        aria-label={t('common.close')}
        title={t('widgetSettings.closeTooltip')}
        className="absolute top-2 right-2 z-10 flex items-center justify-center w-6 h-6 rounded-md border border-border-default bg-bg-deep/80 text-fg-muted hover:text-danger hover:border-danger/50 hover:bg-danger/10 transition-all cursor-pointer"
      >
        <X className="h-3 w-3" />
      </button>
      <div className="flex-1 min-h-0 flex flex-col p-3.5 pt-3 pr-9">
        {kind === 'NEWS' && <NewsConfig config={config} onChange={onChange} />}
        {kind === 'WATCHLIST' && <WatchlistConfig config={config} onChange={onChange} />}
        {kind === 'ASSET_CARDS' && <AssetCardsConfig config={config} onChange={onChange} autoFocusName={autoFocusName} />}
      </div>
      <div className="shrink-0 flex items-center justify-end gap-2 px-3 py-2 border-t border-border-default/60 bg-bg-deep/30 rounded-b-xl">
        <button
          type="button"
          onClick={onClose}
          className="flex items-center gap-1.5 rounded-md border border-accent bg-accent text-white px-2.5 py-1 text-[11px] font-display font-semibold tracking-tight hover:bg-accent-bright shadow-md shadow-accent/20 transition-all cursor-pointer"
        >
          <Check className="h-3 w-3" />
          {t('widgetSettings.done')}
        </button>
      </div>
    </motion.div>,
    document.body,
  );
}
