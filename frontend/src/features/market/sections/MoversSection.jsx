import { memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { BarChart3, ChevronRight } from 'lucide-react';
import { formatPriceCompactTRY, getChangeClass, changeColors, formatPercent } from '../../../shared/utils/formatters';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities' };

function shortLabel(asset) {
  return (asset.code || '').replace('.IS', '');
}

function AssetRow({ asset, color, onClick }) {
  const cls = getChangeClass(asset.changePercent);
  return (
    <button
      type="button"
      onClick={onClick}
      title={asset.name}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface/60 transition-colors group cursor-pointer text-left border-none bg-transparent"
    >
      {asset.image
        ? <img src={asset.image} alt="" loading="lazy" className="w-5 h-5 rounded-full ring-1 ring-border-default shrink-0" />
        : <span
            className="w-5 h-5 rounded-full shrink-0 flex items-center justify-center text-[8px] font-bold text-white shadow-sm"
            style={{ backgroundColor: color }}
          >
            {shortLabel(asset).slice(0, 2)}
          </span>}
      <span className="font-display text-[12px] font-semibold text-fg truncate flex-1 min-w-[60px] group-hover:text-accent transition-colors">
        {shortLabel(asset)}
      </span>
      <span className="font-mono text-[11px] font-bold text-fg tabular-nums shrink-0">{formatPriceCompactTRY(asset.price)}</span>
      {asset.changePercent != null && (
        <span className={`font-mono text-[10px] font-semibold tabular-nums min-w-[48px] text-right shrink-0 ${changeColors[cls]}`}>
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
function MoversSectionImpl({ data }) {
  const navigate = useNavigate();
  const market = data?.market;
  const gainers = data?.gainers ?? [];
  const losers = data?.losers ?? [];
  const color = ASSET_TYPE_COLORS[market] || '#6366f1';
  const label = ASSET_TYPE_LABELS[market] || market || 'Piyasa';

  return (
    <section
      className="group relative rounded-xl border border-border-default border-t-2 bg-bg-elevated overflow-hidden h-full flex flex-col card-hover transition-all duration-200 hover:border-border-hover"
      style={{ borderTopColor: color }}
    >
      <button
        type="button"
        onClick={() => navigate(TYPE_ROUTES[market] ?? '/market')}
        className="flex items-center gap-2 w-full p-3 cursor-pointer hover:bg-surface/30 transition-colors group/title bg-transparent border-x-0 border-t-0 border-b border-border-default"
      >
        <span
          className="flex items-center justify-center w-7 h-7 rounded-lg transition-transform duration-300 group-hover/title:scale-105"
          style={{ backgroundColor: `${color}18`, boxShadow: `0 0 16px ${color}15` }}
        >
          <BarChart3 className="h-3.5 w-3.5" style={{ color }} />
        </span>
        <span className="font-display text-[13px] font-bold text-fg">{label}</span>
        <ChevronRight className="h-3.5 w-3.5 text-fg-subtle ml-auto opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
      </button>
      <div className="grid grid-cols-2 divide-x divide-border-default flex-1 min-h-0">
        <div className="p-2 flex flex-col min-h-0">
          <div className="flex items-center gap-1.5 px-2 pb-1.5 mb-1 shrink-0">
            <span className="relative w-1.5 h-1.5">
              <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-40" />
              <span className="relative block w-1.5 h-1.5 rounded-full bg-success" />
            </span>
            <span className="font-mono text-[9px] uppercase tracking-[0.18em] text-success font-semibold">Yükselen</span>
          </div>
          <div className="space-y-0.5 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
            {gainers.length === 0
              ? <p className="text-[10px] text-fg-subtle px-2 py-3 text-center">Veri yok</p>
              : gainers.map((a) => <AssetRow key={a.code} asset={a} color={color} onClick={() => navigate(`${TYPE_ROUTES[market] ?? '/market'}/${a.code}`)} />)}
          </div>
        </div>
        <div className="p-2 flex flex-col min-h-0">
          <div className="flex items-center gap-1.5 px-2 pb-1.5 mb-1 shrink-0">
            <span className="relative w-1.5 h-1.5">
              <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-40" />
              <span className="relative block w-1.5 h-1.5 rounded-full bg-danger" />
            </span>
            <span className="font-mono text-[9px] uppercase tracking-[0.18em] text-danger font-semibold">Düşen</span>
          </div>
          <div className="space-y-0.5 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
            {losers.length === 0
              ? <p className="text-[10px] text-fg-subtle px-2 py-3 text-center">Veri yok</p>
              : losers.map((a) => <AssetRow key={a.code} asset={a} color={color} onClick={() => navigate(`${TYPE_ROUTES[market] ?? '/market'}/${a.code}`)} />)}
          </div>
        </div>
      </div>
    </section>
  );
}

export default memo(MoversSectionImpl);
