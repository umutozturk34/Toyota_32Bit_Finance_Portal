import { motion } from 'framer-motion';
import { ChevronRight, Package } from 'lucide-react';
import { ArrowUpRight } from '../../shared/components/AnimatedIcons';
import { formatPriceTRY, formatPercent, changeColors, changeBg, getChangeClass } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import { ASSET_TYPE_LABELS, ASSET_TYPE_STYLES } from '../../shared/constants/assetTypes';
import { assetCodeLabel } from '../../shared/utils/assetCode';
import { usePortfolioPositions } from './usePortfolioData';
import useListParams from '../../shared/hooks/useListParams';
import PortfolioListShell from './PortfolioListShell';

const SORT_OPTIONS = [
  { id: 'currentValue', label: 'Piyasa Değeri' },
  { id: 'profitPercent', label: 'K/Z %' },
  { id: 'profitAmount', label: 'K/Z Tutar' },
  { id: 'assetCode', label: 'Varlık Kodu' },
  { id: 'quantity', label: 'Miktar' },
];

function AssetBadge({ pos }) {
  const typeStyle = ASSET_TYPE_STYLES[pos.assetType] || ASSET_TYPE_STYLES.CRYPTO;
  return pos.assetImage ? (
    <img src={pos.assetImage} alt={pos.assetCode} className="w-8 h-8 rounded-lg shrink-0" />
  ) : (
    <span className={`flex items-center justify-center w-8 h-8 rounded-lg ${typeStyle.bg} text-sm font-bold ${typeStyle.text} shrink-0`}>
      {assetCodeLabel(pos.assetType, pos.assetCode).slice(0, 3).toUpperCase()}
    </span>
  );
}

export default function PositionsTable({ portfolioId, onAssetClick, onSellClick }) {
  const listParams = useListParams({ defaultSize: 8, prefix: 'pos' });

  const queryParams = {
    ...listParams.params,
    ...(listParams.filter && { assetType: listParams.filter }),
  };

  const { data } = usePortfolioPositions(portfolioId, queryParams);
  const positions = data?.content || [];
  const totalPages = data?.totalPages || 0;

  if (!portfolioId) return null;

  return (
    <PortfolioListShell
      listParams={listParams}
      totalPages={totalPages}
      sortOptions={SORT_OPTIONS}
      searchPlaceholder="Pozisyon ara..."
      filterLayoutId="pos-type"
      isEmpty={positions.length === 0}
      emptyIcon={<Package className="h-8 w-8 text-fg-muted" />}
      emptyMessage={listParams.search ? 'Aramayla eşleşen pozisyon bulunamadı.' : 'Henüz pozisyon bulunmuyor'}
      emptyHint={!listParams.search ? 'Kripto, Hisse, Döviz veya Fon sayfalarından alım yapabilirsiniz' : undefined}
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

          {positions.map((pos) => {
            const pnlClass = getChangeClass(pos.pnlTry);
            return (
              <motion.div key={pos.id} variants={cardVariants} className="rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md card-hover transition-all duration-200 hover:border-border-hover group">
                <div className="hidden lg:grid lg:grid-cols-[1.3fr_0.7fr_1fr_1fr_1fr_1.2fr_48px_20px] gap-2 items-center p-4 min-w-0">
                  <div className="flex items-center gap-2.5 cursor-pointer min-w-0" onClick={() => onAssetClick(pos)}>
                    <AssetBadge pos={pos} />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-fg leading-tight truncate">{assetCodeLabel(pos.assetType, pos.assetCode)}</p>
                      <p className="text-[11px] text-fg-muted truncate">{pos.assetName && pos.assetName !== pos.assetCode ? pos.assetName : ASSET_TYPE_LABELS[pos.assetType]}</p>
                    </div>
                  </div>
                  <p className="text-right text-[11px] font-mono text-fg truncate">{Number(pos.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</p>
                  <p className="text-right text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.averageCostTry)}</p>
                  <p className="text-right text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.currentPriceTry)}</p>
                  <p className="text-right text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.marketValueTry)}</p>
                  <div className="text-right min-w-0">
                    <p className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]} truncate`}>{formatPriceTRY(pos.pnlTry)}</p>
                    <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(pos.pnlPercent)}</span>
                  </div>
                  <div className="flex justify-center">
                    <button onClick={(e) => { e.stopPropagation(); onSellClick(pos); }} className="flex items-center gap-1 rounded-md px-2 py-1.5 text-[11px] font-semibold text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer">
                      <ArrowUpRight className="h-3 w-3" />Sat
                    </button>
                  </div>
                  <ChevronRight onClick={() => onAssetClick(pos)} className="h-4 w-4 text-fg-muted group-hover:text-accent group-hover:translate-x-1 transition-all cursor-pointer" />
                </div>

                <div className="lg:hidden p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5 min-w-0 flex-1" onClick={() => onAssetClick(pos)}>
                      <AssetBadge pos={pos} />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-fg truncate">{pos.assetCode}</p>
                        <p className="text-[11px] text-fg-muted truncate">{pos.assetName && pos.assetName !== pos.assetCode ? pos.assetName : ASSET_TYPE_LABELS[pos.assetType]}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(pos.pnlPercent)}</span>
                      <button onClick={(e) => { e.stopPropagation(); onSellClick(pos); }} className="flex items-center gap-1 rounded-md px-2 py-1 text-[11px] font-semibold text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer">
                        <ArrowUpRight className="h-3 w-3" />Sat
                      </button>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Miktar</p><p className="font-mono text-fg font-medium">{Number(pos.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</p></div>
                    <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Piyasa Değeri</p><p className="font-mono text-fg font-medium">{formatPriceTRY(pos.marketValueTry)}</p></div>
                    <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Ort. Maliyet</p><p className="font-mono text-fg font-medium">{formatPriceTRY(pos.averageCostTry)}</p></div>
                    <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">K/Z</p><p className={`font-mono font-semibold ${changeColors[pnlClass]}`}>{formatPriceTRY(pos.pnlTry)}</p></div>
                  </div>
                </div>
              </motion.div>
            );
          })}
    </PortfolioListShell>
  );
}
