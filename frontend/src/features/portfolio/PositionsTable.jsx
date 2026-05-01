import { motion } from 'framer-motion';
import { ChevronRight, Package, Pencil, Trash2 } from 'lucide-react';
import { Loader2 } from '../../shared/components/AnimatedIcons';
import { formatPriceTRY, formatPercent, changeColors, changeBg, getChangeClass } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import { ASSET_TYPE_LABELS, ASSET_TYPE_STYLES } from '../../shared/constants/assetTypes';
import { assetCodeLabel } from '../../shared/utils/assetCode';
import { isLotPending, useBackfillStatus, usePortfolioPositions } from './usePortfolioData';
import useListParams from '../../shared/hooks/useListParams';
import useElapsedSeconds from '../../shared/hooks/useElapsedSeconds';
import PortfolioListShell from './PortfolioListShell';

const SORT_OPTIONS = [
  { id: 'currentValue', label: 'Piyasa Değeri' },
  { id: 'profitPercent', label: 'K/Z %' },
  { id: 'profitAmount', label: 'K/Z Tutar' },
  { id: 'entryDate', label: 'Tarih' },
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

function formatEntryDate(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: '2-digit' });
}

export default function PositionsTable({ portfolioId, onAssetClick: assetClickProp, onEditClick: editClickProp, onDeleteClick: deleteClickProp }) {
  const listParams = useListParams({ defaultSize: 8, prefix: 'pos' });

  const queryParams = {
    ...listParams.params,
    ...(listParams.filter && { assetType: listParams.filter }),
  };

  const { data } = usePortfolioPositions(portfolioId, queryParams);
  const positions = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const backfill = useBackfillStatus(portfolioId);
  const elapsed = useElapsedSeconds(backfill.since);

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
      emptyHint={!listParams.search ? 'Kripto, Hisse, Döviz veya Fon sayfalarından lot ekleyebilirsiniz' : undefined}
    >
      <div className="space-y-3">
      <div className="hidden lg:grid lg:grid-cols-[1.3fr_0.7fr_1fr_1fr_1fr_1fr_1.2fr_72px_20px] gap-2 px-4 py-2 text-xs text-fg-muted font-medium">
        <span>Varlık</span>
        <span className="text-right">Miktar</span>
        <span className="text-right">Giriş Tarihi</span>
        <span className="text-right">Giriş Fiyatı</span>
        <span className="text-right">Güncel Fiyat</span>
        <span className="text-right">Piyasa Değeri</span>
        <span className="text-right">K/Z</span>
        <span className="text-center">İşlem</span>
        <span />
      </div>

      {positions.map((pos) => (
        <PositionRow
          key={pos.id}
          pos={pos}
          pending={isLotPending(backfill, pos.assetType, pos.assetCode)}
          elapsed={elapsed}
          onAssetClick={assetClickProp}
          onEditClick={editClickProp}
          onDeleteClick={deleteClickProp}
        />
      ))}
      </div>
    </PortfolioListShell>
  );
}

function PositionRow({ pos, pending, elapsed, onAssetClick, onEditClick, onDeleteClick }) {
  const pnlClass = getChangeClass(pos.pnlTry);

  const guard = (fn) => () => { if (!pending) fn(pos); };
  const assetClick = guard(onAssetClick);
  const editClick = guard(onEditClick);
  const deleteClick = guard(onDeleteClick);

  return (
    <motion.div
      variants={cardVariants}
      className={`relative rounded-2xl border bg-bg-elevated backdrop-blur-md transition-all duration-200 group overflow-hidden ${pending ? 'border-accent/40 pointer-events-none' : 'border-border-default card-hover hover:border-border-hover'}`}
    >
      {pending && (
        <>
          <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-accent/60 to-transparent animate-pulse z-10" />
          <div className="absolute top-2.5 left-3 right-3 flex items-center justify-between text-[11px] font-medium text-accent z-10 pointer-events-none">
            <div className="flex items-center gap-1.5">
              <Loader2 className="h-3 w-3 animate-spin" />
              <span className="tracking-tight">veriler hazırlanıyor</span>
            </div>
            <motion.span
              key={elapsed}
              initial={{ opacity: 0.4, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
              className="font-mono tabular-nums text-accent/80"
            >
              {String(elapsed).padStart(2, '0')}s
            </motion.span>
          </div>
        </>
      )}
      <div className={pending ? 'pt-7 opacity-50' : ''}>
      <div className="hidden lg:grid lg:grid-cols-[1.3fr_0.7fr_1fr_1fr_1fr_1fr_1.2fr_72px_20px] gap-2 items-center p-4 min-w-0">
        <div className="flex items-center gap-2.5 cursor-pointer min-w-0" onClick={assetClick}>
          <AssetBadge pos={pos} />
          <div className="min-w-0">
            <p className="text-sm font-semibold text-fg leading-tight truncate">{assetCodeLabel(pos.assetType, pos.assetCode)}</p>
            <p className="text-[11px] text-fg-muted truncate">{pos.assetName && pos.assetName !== pos.assetCode ? pos.assetName : ASSET_TYPE_LABELS[pos.assetType]}</p>
          </div>
        </div>
        <p className="text-right text-[11px] font-mono text-fg truncate">{Number(pos.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</p>
        <p className="text-right text-[11px] font-mono text-fg-muted truncate">{formatEntryDate(pos.entryDate)}</p>
        <p className="text-right text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.entryPrice)}</p>
        <p className="text-right text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.currentPriceTry)}</p>
        <p className="text-right text-[11px] font-mono text-fg truncate">{formatPriceTRY(pos.marketValueTry)}</p>
        <div className="text-right min-w-0">
          <p className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]} truncate`}>{formatPriceTRY(pos.pnlTry)}</p>
          <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(pos.pnlPercent)}</span>
        </div>
        <div className="flex justify-center gap-1">
          <button onClick={(e) => { e.stopPropagation(); editClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label="Düzenle">
            <Pencil className="h-3 w-3" />
          </button>
          <button onClick={(e) => { e.stopPropagation(); deleteClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label="Sil">
            <Trash2 className="h-3 w-3" />
          </button>
        </div>
        <ChevronRight onClick={assetClick} className="h-4 w-4 text-fg-muted group-hover:text-accent group-hover:translate-x-1 transition-all cursor-pointer" />
      </div>

      <div className="lg:hidden p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5 min-w-0 flex-1" onClick={assetClick}>
            <AssetBadge pos={pos} />
            <div className="min-w-0">
              <p className="text-sm font-semibold text-fg truncate">{pos.assetCode}</p>
              <p className="text-[11px] text-fg-muted truncate">{pos.assetName && pos.assetName !== pos.assetCode ? pos.assetName : ASSET_TYPE_LABELS[pos.assetType]}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(pos.pnlPercent)}</span>
            <button onClick={(e) => { e.stopPropagation(); editClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label="Düzenle">
              <Pencil className="h-3 w-3" />
            </button>
            <button onClick={(e) => { e.stopPropagation(); deleteClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label="Sil">
              <Trash2 className="h-3 w-3" />
            </button>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Miktar</p><p className="font-mono text-fg font-medium">{Number(pos.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Giriş Tarihi</p><p className="font-mono text-fg font-medium">{formatEntryDate(pos.entryDate)}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Giriş Fiyatı</p><p className="font-mono text-fg font-medium">{formatPriceTRY(pos.entryPrice)}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">K/Z</p><p className={`font-mono font-semibold ${changeColors[pnlClass]}`}>{formatPriceTRY(pos.pnlTry)}</p></div>
        </div>
      </div>
      </div>
    </motion.div>
  );
}
