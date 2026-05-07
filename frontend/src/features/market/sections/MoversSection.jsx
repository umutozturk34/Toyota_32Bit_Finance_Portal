import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { TrendingUp, ChevronRight } from 'lucide-react';
import { formatPriceTRY, getChangeClass, changeColors, formatPercent } from '../../../shared/utils/formatters';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities' };

function shortLabel(asset) {
  return (asset.code || '').replace('.IS', '');
}

function Row({ asset, onClick }) {
  const cls = getChangeClass(asset.changePercent);
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-surface/60 transition-colors cursor-pointer text-left border-none bg-transparent"
    >
      <span className="font-mono text-[11px] font-semibold text-fg truncate flex-1" title={asset.name}>{shortLabel(asset)}</span>
      <span className="font-mono text-[11px] font-bold text-fg tabular-nums">{formatPriceTRY(asset.price)}</span>
      {asset.changePercent != null && (
        <span className={`font-mono text-[10px] font-semibold tabular-nums min-w-[44px] text-right ${changeColors[cls]}`}>
          {formatPercent(asset.changePercent)}
        </span>
      )}
    </button>
  );
}

/**
 * @typedef {Object} MoversSectionProps
 * @property {{market: string, gainers: Array<Object>, losers: Array<Object>}|null} data
 */

/** @param {MoversSectionProps} props */
export default function MoversSection({ data }) {
  const navigate = useNavigate();
  const market = data?.market;
  const gainers = data?.gainers ?? [];
  const losers = data?.losers ?? [];
  const color = ASSET_TYPE_COLORS[market] || '#6366f1';
  const label = ASSET_TYPE_LABELS[market] || market || 'Piyasa';

  return (
    <motion.section
      className="relative rounded-xl border border-border-default bg-bg-elevated overflow-hidden hover:border-border-hover transition-colors"
    >
      <div className="absolute top-0 left-0 right-0 h-[1px]" style={{ background: `linear-gradient(90deg, ${color}, ${color}30 60%, transparent)` }} />
      <button
        type="button"
        onClick={() => navigate(TYPE_ROUTES[market] ?? '/market')}
        className="flex items-center gap-2 w-full px-3 py-2 border-b border-border-default bg-transparent border-x-0 cursor-pointer hover:bg-surface/40 transition-colors group"
      >
        <span className="flex items-center justify-center w-6 h-6 rounded-md" style={{ backgroundColor: `${color}18` }}>
          <TrendingUp className="h-3 w-3" style={{ color }} />
        </span>
        <span className="text-[11px] font-bold text-fg uppercase tracking-wider">{label}</span>
        <ChevronRight className="h-3 w-3 text-fg-subtle ml-auto opacity-0 group-hover:opacity-100 transition-opacity" />
      </button>
      <div className="grid grid-cols-2 divide-x divide-border-default">
        <div className="p-2">
          <div className="flex items-center gap-1 px-2 pb-1.5 mb-1">
            <span className="w-1 h-1 rounded-full bg-success animate-pulse" />
            <span className="font-mono text-[9px] uppercase tracking-wider text-success">Yükselen</span>
          </div>
          <div className="space-y-0.5 max-h-[180px] overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
            {gainers.length === 0
              ? <p className="text-[10px] text-fg-subtle px-2 py-2 text-center">—</p>
              : gainers.map((a) => <Row key={a.code} asset={a} onClick={() => navigate(`${TYPE_ROUTES[market] ?? '/market'}/${a.code}`)} />)}
          </div>
        </div>
        <div className="p-2">
          <div className="flex items-center gap-1 px-2 pb-1.5 mb-1">
            <span className="w-1 h-1 rounded-full bg-danger animate-pulse" />
            <span className="font-mono text-[9px] uppercase tracking-wider text-danger">Düşen</span>
          </div>
          <div className="space-y-0.5 max-h-[180px] overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
            {losers.length === 0
              ? <p className="text-[10px] text-fg-subtle px-2 py-2 text-center">—</p>
              : losers.map((a) => <Row key={a.code} asset={a} onClick={() => navigate(`${TYPE_ROUTES[market] ?? '/market'}/${a.code}`)} />)}
          </div>
        </div>
      </div>
    </motion.section>
  );
}
