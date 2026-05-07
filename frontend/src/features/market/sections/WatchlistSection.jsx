import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Bookmark, ChevronRight } from 'lucide-react';
import { formatPriceTRY, getChangeClass, changeColors, formatPercent } from '../../../shared/utils/formatters';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities' };

function shortLabel(item) {
  return (item.assetCode || '').replace('.IS', '');
}

function Row({ item, onClick }) {
  const cls = getChangeClass(item.changePercent);
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-surface/60 transition-colors cursor-pointer text-left border-none bg-transparent"
    >
      <span className="font-mono text-[11px] font-semibold text-fg truncate flex-1">{shortLabel(item)}</span>
      <span className="font-mono text-[10px] uppercase text-fg-subtle">{item.marketType}</span>
      <span className="font-mono text-[11px] font-bold text-fg tabular-nums">{formatPriceTRY(item.price)}</span>
      {item.changePercent != null && (
        <span className={`font-mono text-[10px] font-semibold tabular-nums min-w-[44px] text-right ${changeColors[cls]}`}>
          {formatPercent(item.changePercent)}
        </span>
      )}
    </button>
  );
}

/**
 * @typedef {Object} WatchlistSectionProps
 * @property {{watchlistId: number|null, watchlistName: string, items: Array<Object>}|null} data
 */

/** @param {WatchlistSectionProps} props */
export default function WatchlistSection({ data }) {
  const navigate = useNavigate();
  const items = data?.items ?? [];
  const name = data?.watchlistName ?? 'Takip Listesi';

  return (
    <motion.section className="relative rounded-xl border border-border-default bg-bg-elevated overflow-hidden hover:border-border-hover transition-colors">
      <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-accent via-accent/30 to-transparent" />
      <button
        type="button"
        onClick={() => navigate('/watch')}
        className="flex items-center gap-2 w-full px-3 py-2 border-b border-border-default bg-transparent border-x-0 cursor-pointer hover:bg-surface/40 transition-colors group"
      >
        <span className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/15">
          <Bookmark className="h-3 w-3 text-accent" />
        </span>
        <span className="text-[11px] font-bold text-fg truncate uppercase tracking-wider">{name}</span>
        <ChevronRight className="h-3 w-3 text-fg-subtle ml-auto opacity-0 group-hover:opacity-100 transition-opacity" />
      </button>
      <div className="p-2 max-h-[220px] overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
        {items.length === 0
          ? <p className="text-[11px] text-fg-subtle py-4 text-center">Henüz takip varlığı yok</p>
          : <div className="space-y-0.5">
              {items.map((it) => (
                <Row
                  key={`${it.marketType}-${it.assetCode}`}
                  item={it}
                  onClick={() => navigate(`${TYPE_ROUTES[it.marketType] ?? '/market'}/${it.assetCode}`)}
                />
              ))}
            </div>
        }
      </div>
    </motion.section>
  );
}
