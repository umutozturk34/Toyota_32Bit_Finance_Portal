import { memo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Bookmark, ChevronRight } from 'lucide-react';
import { formatPriceTRY, getChangeClass, changeColors, formatPercent } from '../../../shared/utils/formatters';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities' };

function shortLabel(item) {
  return (item.assetCode || '').replace('.IS', '');
}

function ItemRow({ item, color, onClick }) {
  const cls = getChangeClass(item.changePercent);
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface/60 transition-colors cursor-pointer text-left border-none bg-transparent group"
    >
      {item.image
        ? <img src={item.image} alt="" loading="lazy" className="w-6 h-6 rounded-full ring-1 ring-border-default shrink-0" />
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
        <span className="font-mono text-[9px] uppercase tracking-[0.14em] text-fg-subtle leading-tight">{item.marketType}</span>
      </div>
      <div className="flex flex-col items-end shrink-0">
        <span className="font-mono text-[12px] font-bold text-fg tabular-nums leading-tight">{formatPriceTRY(item.price)}</span>
        {item.changePercent != null && (
          <span className={`font-mono text-[10px] font-semibold tabular-nums leading-tight ${changeColors[cls]}`}>
            {formatPercent(item.changePercent)}
          </span>
        )}
      </div>
    </button>
  );
}

/**
 * @typedef {Object} WatchlistSectionProps
 * @property {{watchlistId: number|null, watchlistName: string, items: Array<Object>}|null} data
 */

/** @param {WatchlistSectionProps} props */
function WatchlistSectionImpl({ data }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const items = data?.items ?? [];
  const name = data?.watchlistName ?? t('watchlistSection.fallbackName');

  return (
    <section className="group relative rounded-xl border border-border-default border-t-2 border-t-accent bg-bg-elevated overflow-hidden h-full flex flex-col card-hover transition-all duration-200 hover:border-border-hover">
      <button
        type="button"
        onClick={() => navigate('/watch')}
        className="flex items-center gap-2 w-full p-3 cursor-pointer hover:bg-surface/30 transition-colors group/title bg-transparent border-x-0 border-t-0 border-b border-border-default shrink-0"
      >
        <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent/15 shadow-[0_0_16px_-4px_var(--color-accent)]/30">
          <Bookmark className="h-3.5 w-3.5 text-accent" />
        </span>
        <span className="font-display text-[13px] font-bold text-fg truncate">{name}</span>
        <ChevronRight className="h-3.5 w-3.5 text-fg-subtle ml-auto opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
      </button>
      <div className="p-2 flex-1 min-h-0 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
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
                    onClick={() => navigate(`${TYPE_ROUTES[it.marketType] ?? '/market'}/${it.assetCode}`)}
                  />
                );
              })}
            </div>
        }
      </div>
    </section>
  );
}

export default memo(WatchlistSectionImpl);
