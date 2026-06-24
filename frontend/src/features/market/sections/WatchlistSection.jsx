import { memo, useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Bookmark, ChevronRight, Plus, Trash2 } from 'lucide-react';
import { getChangeClass, changeColors, formatPercent } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { priceCurrencyOf } from '../../../shared/utils/priceCurrency';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import { localizeWatchlistName } from '../../../shared/utils/watchlistName';
import useNavigationStore from '../../../shared/stores/useNavigationStore';
import Card from '../../../shared/components/card';
import AddWatchlistItemModal from '../../watch/components/AddWatchlistItemModal';
import { useRemoveWatchlistItem } from '../../../shared/hooks/useWatchlist';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };

function shortLabel(item) {
  return (item.assetCode || '').replace('.IS', '');
}

function ItemRow({ item, color, onClick, onRemove }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const cls = getChangeClass(item.changePercent);
  return (
    <div className="relative group">
      <button
        type="button"
        onClick={onClick}
        className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface/60 transition-colors cursor-pointer text-left border-none bg-transparent"
      >
      {item.image
        ? (/^https?:\/\//i.test(item.image)
            ? <img src={item.image} alt="" loading="lazy" className="w-6 h-6 rounded-full ring-1 ring-border-default shrink-0" />
            : <span className="w-6 h-6 rounded-full shrink-0 flex items-center justify-center text-base leading-none">{item.image}</span>)
        : <span
            className="w-6 h-6 rounded-full shrink-0 flex items-center justify-center text-[9px] font-bold text-white shadow-sm"
            style={{ backgroundColor: color }}
          >
            {shortLabel(item).slice(0, 2)}
          </span>}
      <div className="flex flex-col min-w-0 flex-1">
        <span className="font-display text-[12px] font-semibold text-fg truncate group-hover:text-accent transition-colors leading-tight">
          {shortLabel(item)}
        </span>
        <span className="font-mono text-[9px] uppercase tracking-[0.14em] text-fg-subtle leading-tight">{t(`assets.labels.${item.marketType}`, { defaultValue: item.marketType })}</span>
      </div>
      <div className="flex flex-col items-end shrink-0">
        <span className="font-mono text-[12px] font-bold text-fg tabular-nums leading-tight">{money(item.price, priceCurrencyOf(item))}</span>
        {item.changePercent != null && (
          <span className={`font-mono text-[10px] font-semibold tabular-nums leading-tight ${changeColors[cls]}`}>
            {formatPercent(item.changePercent)}
          </span>
        )}
      </div>
      </button>
      {item.id != null && onRemove && (
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); onRemove(item.id); }}
          onPointerDown={(e) => e.stopPropagation()}
          title={t('watchlistSection.removeAsset', { defaultValue: 'Remove' })}
          aria-label={t('watchlistSection.removeAsset', { defaultValue: 'Remove' })}
          className="absolute right-1.5 top-1/2 -translate-y-1/2 flex items-center justify-center w-6 h-6 rounded-md bg-bg-elevated border border-border-default text-fg-subtle hover:text-danger hover:border-danger/40 opacity-0 group-hover:opacity-100 transition-all cursor-pointer"
        >
          <Trash2 className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}

function WatchlistSectionImpl({ data }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setOrigin = useNavigationStore((s) => s.setOrigin);
  const [showAdd, setShowAdd] = useState(false);
  const listId = data?.watchlistId ?? null;
  const removeItem = useRemoveWatchlistItem(listId);
  const items = data?.items ?? [];
  const name = data?.watchlistName ? localizeWatchlistName(t, data.watchlistName) : t('watchlistSection.fallbackName');
  const goToAsset = useCallback((marketType, assetCode) => {
    setOrigin('/market', window.scrollY);
    navigate(`${TYPE_ROUTES[marketType] ?? '/market'}/${assetCode}`, { state: { from: '/market' } });
  }, [navigate, setOrigin]);

  return (
    <Card as="section" accentBar="accent" radius="xl" padding="none" className="group h-full flex flex-col">
      <div className="flex items-center gap-1 p-3 border-b border-border-default shrink-0">
        <button
          type="button"
          onClick={() => navigate('/watch')}
          className="flex items-center gap-2 flex-1 min-w-0 cursor-pointer group/title bg-transparent border-none p-0 text-left"
        >
          <span className="flex items-center justify-center w-7 h-7 shrink-0 rounded-lg bg-accent/15 shadow-[0_0_16px_-4px_var(--color-accent)]/30">
            <Bookmark className="h-3.5 w-3.5 text-accent" />
          </span>
          <span className="font-display text-[13px] font-bold text-fg truncate min-w-0 flex-1">{name}</span>
          <ChevronRight className="h-3.5 w-3.5 text-fg-subtle shrink-0 opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
        </button>
        <button
          type="button"
          onClick={() => setShowAdd(true)}
          onPointerDown={(e) => e.stopPropagation()}
          title={t('watchlistSection.addAsset', { defaultValue: 'Add asset' })}
          aria-label={t('watchlistSection.addAsset', { defaultValue: 'Add asset' })}
          className="flex items-center justify-center w-7 h-7 shrink-0 rounded-lg border border-border-default text-fg-muted hover:text-accent hover:border-border-hover bg-transparent transition-colors cursor-pointer"
        >
          <Plus className="h-4 w-4" />
        </button>
      </div>
      <div className="p-2 flex-1 min-h-0 overflow-y-auto scrollbar-auto-hide">
        {items.length === 0
          ? <p className="text-[11px] text-fg-subtle py-5 text-center">{t('watchlistSection.empty')}</p>
          : <div className="space-y-0.5">
              {items.map((it) => {
                const color = ASSET_TYPE_COLORS[it.marketType] || '#6366f1';
                return (
                  <ItemRow
                    key={`${it.marketType}-${it.assetCode}`}
                    item={it}
                    color={color}
                    onClick={() => goToAsset(it.marketType, it.assetCode)}
                    onRemove={(id) => removeItem.mutate(id)}
                  />
                );
              })}
            </div>
        }
      </div>
      <AddWatchlistItemModal isOpen={showAdd} onClose={() => setShowAdd(false)} watchlistId={listId} />
    </Card>
  );
}

export default memo(WatchlistSectionImpl);
