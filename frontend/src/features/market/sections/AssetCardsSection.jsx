import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Layers } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight } from '../../../shared/components/AnimatedIcons';
import { formatPriceTRY, getChangeClass, changeColors, changeBg, formatPercentAbs } from '../../../shared/utils/formatters';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities' };

function shortLabel(asset) {
  return (asset.code || '').replace('.IS', '');
}

function AssetCard({ asset, index, onClick }) {
  const cls = getChangeClass(asset.changePercent);
  const isUp = (asset.changePercent ?? 0) > 0;
  const accent = isUp ? '#10b981' : '#ef4444';
  return (
    <motion.button
      type="button"
      onClick={onClick}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.32, delay: 0.04 * index, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.98 }}
      className="relative flex flex-col items-start min-w-[148px] rounded-xl border border-border-default bg-bg-elevated px-3 py-2.5 cursor-pointer hover:border-border-hover transition-colors text-left overflow-hidden group"
    >
      <div
        className="pointer-events-none absolute inset-0 opacity-0 group-hover:opacity-30 transition-opacity"
        style={{ background: `linear-gradient(135deg, ${accent}10, transparent 60%)` }}
      />
      <div className="relative flex items-center justify-between w-full mb-1">
        <span className="font-mono text-[11px] font-bold tracking-tight text-fg">{shortLabel(asset)}</span>
        <span className="text-[9px] uppercase tracking-wider text-fg-subtle">{asset.type}</span>
      </div>
      <span className="relative font-mono text-base font-bold text-fg tabular-nums">
        {formatPriceTRY(asset.price)}
      </span>
      {asset.changePercent != null && (
        <span className={`relative mt-1 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] font-mono font-semibold tabular-nums ${changeBg[cls]} ${changeColors[cls]}`}>
          {isUp ? <ArrowUpRight className="h-2.5 w-2.5" /> : <ArrowDownRight className="h-2.5 w-2.5" />}
          {formatPercentAbs(asset.changePercent)}
        </span>
      )}
    </motion.button>
  );
}

/**
 * @typedef {Object} AssetCardsSectionProps
 * @property {{items: Array<Object>}|null} data
 */

/** @param {AssetCardsSectionProps} props */
export default function AssetCardsSection({ data }) {
  const navigate = useNavigate();
  const items = data?.items ?? [];

  if (items.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border-default bg-bg-elevated/40 px-4 py-5 text-center">
        <Layers className="inline-block h-3.5 w-3.5 text-fg-subtle mb-1" />
        <p className="text-[11px] text-fg-subtle font-mono uppercase tracking-wider">Henüz varlık eklenmemiş</p>
        <p className="text-[10px] text-fg-subtle mt-1">Düzenleme modunda <strong className="text-accent">Ayarlar</strong> ile asset ekle</p>
      </div>
    );
  }

  const goToAsset = (asset) => navigate(`${TYPE_ROUTES[asset.type] ?? '/market'}/${asset.code}`);

  return (
    <div className="flex gap-2 overflow-x-auto pb-2" style={{ scrollbarWidth: 'thin' }}>
      {items.map((asset, i) => (
        <AssetCard key={`${asset.type}-${asset.code}`} asset={asset} index={i} onClick={() => goToAsset(asset)} />
      ))}
    </div>
  );
}
