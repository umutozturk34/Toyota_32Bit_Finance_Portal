import { useState, useMemo } from 'react';
import { motion } from 'framer-motion';
import { ChevronRight, Package, Filter, ArrowUpRight } from 'lucide-react';
import { formatPriceTRY, formatPercent, changeColors, changeBg, getChangeClass } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import EmptyState from '../../shared/components/EmptyState';

function getAssetDisplay(pos) {
  return {
    badge: pos.assetImage || null,
    badgeText: pos.assetCode.replace('.IS', '').slice(0, 3).toUpperCase(),
    label: pos.assetType === 'CRYPTO' ? (pos.assetCode) : pos.assetCode,
    sub: pos.assetName || null,
  };
}

const ASSET_TYPE_LABELS = {
  CRYPTO: 'Kripto',
  STOCK: 'Hisse',
  FOREX: 'Döviz',
  FUND: 'Fon',
};

const ASSET_TYPE_STYLES = {
  CRYPTO: { bg: 'bg-warning/10', text: 'text-warning' },
  STOCK: { bg: 'bg-success/10', text: 'text-success' },
  FOREX: { bg: 'bg-cyan-400/10', text: 'text-cyan-400' },
  FUND: { bg: 'bg-violet-400/10', text: 'text-violet-400' },
};

const FILTERS = [
  { id: 'ALL', label: 'Tümü' },
  { id: 'CRYPTO', label: 'Kripto' },
  { id: 'STOCK', label: 'Hisse' },
  { id: 'FOREX', label: 'Döviz' },
  { id: 'FUND', label: 'Fon' },
];

export default function PositionsTable({ positions, onAssetClick, onSellClick }) {
  const [filter, setFilter] = useState('ALL');

  const filteredPositions = useMemo(() => {
    if (!positions) return [];
    if (filter === 'ALL') return positions;
    return positions.filter((p) => p.assetType === filter);
  }, [positions, filter]);

  const typeCounts = useMemo(() => {
    const counts = { ALL: positions?.length || 0 };
    (positions || []).forEach((p) => {
      counts[p.assetType] = (counts[p.assetType] || 0) + 1;
    });
    return counts;
  }, [positions]);

  if (!positions || positions.length === 0) {
    return (
      <EmptyState
        icon={<Package className="h-8 w-8 text-fg-muted" />}
        message="Henüz pozisyon bulunmuyor"
        hint="Kripto, Hisse, Döviz veya Fon sayfalarından alım yapabilirsiniz"
      />
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 flex-wrap">
          <Filter className="h-3.5 w-3.5 text-fg-muted" />
          {FILTERS.map(({ id, label }) => {
            const count = typeCounts[id] || 0;
            if (id !== 'ALL' && count === 0) return null;
            return (
              <button
                key={id}
                onClick={() => setFilter(id)}
                className="relative flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent"
              >
                {filter === id && (
                  <motion.span
                    layoutId="pos-filter"
                    className="absolute inset-0 rounded-lg bg-accent/15"
                    transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                  />
                )}
                <span className={`relative z-10 ${filter === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                  {label}
                </span>
                <span className={`relative z-10 text-[10px] opacity-60 ${filter === id ? 'text-accent' : 'text-fg-muted'}`}>{count}</span>
              </button>
            );
          })}
        </div>
      </div>

      <motion.div
        variants={containerVariants(0.04)}
        initial="hidden"
        animate="show"
        className="space-y-2"
      >
        <div className="hidden lg:grid lg:grid-cols-[1.3fr_0.7fr_1fr_1fr_1fr_1.2fr_48px_20px] gap-2 px-4 py-2 text-xs text-fg-muted font-medium">
          <span>Varlık</span>
          <span className="text-right">Miktar</span>
          <span className="text-right">Ort. Maliyet</span>
          <span className="text-right">Güncel Fiyat</span>
          <span className="text-right">Piyasa Değeri</span>
          <span className="text-right">K/Z</span>
          <span className="text-center">Sat</span>
          <span />
        </div>

        {filteredPositions.map((pos) => {
          const pnlClass = getChangeClass(pos.pnlTry);
          const typeStyle = ASSET_TYPE_STYLES[pos.assetType] || ASSET_TYPE_STYLES.CRYPTO;
          const display = getAssetDisplay(pos);

          return (
            <motion.div
              key={pos.id}
              variants={cardVariants}
              className="rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md card-hover transition-all duration-200 hover:border-border-hover group"
            >
              <div className="hidden lg:grid lg:grid-cols-[1.3fr_0.7fr_1fr_1fr_1fr_1.2fr_48px_20px] gap-2 items-center p-4 min-w-0 overflow-hidden">
                <div
                  className="flex items-center gap-2.5 cursor-pointer min-w-0"
                  onClick={() => onAssetClick(pos)}
                >
                  {display.badge ? (
                    <img src={display.badge} alt={display.label} className="w-8 h-8 rounded-lg shrink-0" />
                  ) : (
                    <span className={`flex items-center justify-center w-8 h-8 rounded-lg ${typeStyle.bg} text-sm font-bold ${typeStyle.text} shrink-0`}>
                      {display.badgeText}
                    </span>
                  )}
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-fg leading-tight truncate">{display.label}</p>
                    <p className="text-[11px] text-fg-muted truncate">{display.sub || ASSET_TYPE_LABELS[pos.assetType] || pos.assetType}</p>
                  </div>
                </div>
                <div className="text-right min-w-0">
                  <p className="text-[11px] font-mono text-fg truncate">{Number(pos.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</p>
                </div>
                <div className="text-right min-w-0">
                  <p className="text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.averageCostTry)}</p>
                </div>
                <div className="text-right min-w-0">
                  <p className="text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.currentPriceTry)}</p>
                </div>
                <div className="text-right min-w-0">
                  <p className="text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.marketValueTry)}</p>
                </div>
                <div className="text-right min-w-0">
                  <p className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]} truncate`}>
                    {formatPriceTRY(pos.pnlTry)}
                  </p>
                  <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
                    {formatPercent(pos.pnlPercent)}
                  </span>
                </div>
                <div className="flex justify-center">
                  <button
                    onClick={(e) => { e.stopPropagation(); onSellClick(pos); }}
                    className="flex items-center gap-1 rounded-md px-2 py-1.5 text-[11px] font-semibold text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer"
                  >
                    <ArrowUpRight className="h-3 w-3" />
                    Sat
                  </button>
                </div>
                <div className="flex justify-end">
                  <ChevronRight
                    onClick={() => onAssetClick(pos)}
                    className="h-4 w-4 text-fg-muted group-hover:text-accent group-hover:translate-x-1 transition-all cursor-pointer"
                  />
                </div>
              </div>

              <div className="lg:hidden p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5 min-w-0 flex-1" onClick={() => onAssetClick(pos)}>
                    {display.badge ? (
                      <img src={display.badge} alt={display.label} className="w-9 h-9 rounded-lg shrink-0" />
                    ) : (
                      <span className={`flex items-center justify-center w-9 h-9 rounded-lg ${typeStyle.bg} text-sm font-bold ${typeStyle.text} shrink-0`}>
                        {display.badgeText}
                      </span>
                    )}
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-fg truncate">{display.label}</p>
                      <p className="text-[11px] text-fg-muted truncate">{display.sub || ASSET_TYPE_LABELS[pos.assetType] || pos.assetType}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
                      {formatPercent(pos.pnlPercent)}
                    </span>
                    <button
                      onClick={(e) => { e.stopPropagation(); onSellClick(pos); }}
                      className="flex items-center gap-1 rounded-md px-2 py-1 text-[11px] font-semibold text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer"
                    >
                      <ArrowUpRight className="h-3 w-3" />
                      Sat
                    </button>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div className="rounded-lg bg-bg-base px-2.5 py-2">
                    <p className="text-fg-muted mb-0.5">Miktar</p>
                    <p className="font-mono text-fg font-medium">{Number(pos.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</p>
                  </div>
                  <div className="rounded-lg bg-bg-base px-2.5 py-2">
                    <p className="text-fg-muted mb-0.5">Piyasa Değeri</p>
                    <p className="font-mono text-fg font-medium">{formatPriceTRY(pos.marketValueTry)}</p>
                  </div>
                  <div className="rounded-lg bg-bg-base px-2.5 py-2">
                    <p className="text-fg-muted mb-0.5">Ort. Maliyet</p>
                    <p className="font-mono text-fg font-medium">{formatPriceTRY(pos.averageCostTry)}</p>
                  </div>
                  <div className="rounded-lg bg-bg-base px-2.5 py-2">
                    <p className="text-fg-muted mb-0.5">K/Z</p>
                    <p className={`font-mono font-semibold ${changeColors[pnlClass]}`}>{formatPriceTRY(pos.pnlTry)}</p>
                  </div>
                </div>
              </div>
            </motion.div>
          );
        })}
      </motion.div>
    </div>
  );
}
