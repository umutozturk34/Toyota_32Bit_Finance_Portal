import { useState } from 'react';
import { ChevronRight, Package, Pencil, Trash2, XCircle } from 'lucide-react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { formatPercent, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { cardVariants } from '../../../shared/utils/animations';
import { ASSET_TYPE_STYLES } from '../../../shared/constants/assetTypes';
import { assetCodeLabel } from '../../../shared/utils/assetCode';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import { isLotPending, useBackfillStatus, usePortfolioPositions } from '../hooks/usePortfolioData';
import useListParams from '../../../shared/hooks/useListParams';
import useElapsedSeconds from '../../../shared/hooks/useElapsedSeconds';
import PortfolioListShell from './PortfolioListShell';

const SORT_OPTION_IDS = ['currentValue', 'profitPercent', 'profitAmount', 'entryDate', 'assetCode', 'quantity'];

function AssetBadge({ pos }) {
  const typeStyle = ASSET_TYPE_STYLES[pos.assetType] || ASSET_TYPE_STYLES.CRYPTO;
  if (pos.assetImage) {
    return /^https?:\/\//i.test(pos.assetImage)
      ? <img src={pos.assetImage} alt={pos.assetCode} className="w-8 h-8 rounded-lg shrink-0" />
      : <span className="flex items-center justify-center w-8 h-8 rounded-lg text-xl shrink-0">{pos.assetImage}</span>;
  }
  return (
    <span className={`flex items-center justify-center w-8 h-8 rounded-lg ${typeStyle.bg} text-sm font-bold ${typeStyle.text} shrink-0`}>
      {assetCodeLabel(pos.assetType, pos.assetCode).slice(0, 3).toUpperCase()}
    </span>
  );
}

function formatEntryDate(dateStr, localeTag) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: '2-digit' });
}

function DerivativeChips({ meta, money, t, localeTag }) {
  if (!meta) return null;
  const isLong = meta.direction === 'LONG';
  const formatExpiry = (iso) => {
    if (!iso) return null;
    return new Date(iso).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: '2-digit' });
  };
  return (
    <div className="flex flex-wrap items-center gap-1.5 mt-1.5">
      <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide border ${
        isLong
          ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
          : 'bg-rose-500/15 text-rose-400 border-rose-500/30'
      }`}>
        {meta.direction}
      </span>
      {meta.contractKind && (
        <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-mono text-fg-muted bg-bg-elevated border border-border-default">
          {meta.contractKind}
        </span>
      )}
      {meta.contractSize != null && Number(meta.contractSize) !== 1 && (
        <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-mono text-fg-muted bg-bg-elevated border border-border-default">
          {Number(meta.contractSize).toLocaleString(localeTag)}×
        </span>
      )}
      {meta.lockedMarginTry != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-accent bg-accent/10 border border-accent/30">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.margin', 'Teminat')}</span>
          <span className="font-mono">{money(meta.lockedMarginTry)}</span>
        </span>
      )}
      {meta.expiryDate && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-fg-muted bg-bg-elevated border border-border-default">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.expiry', 'Vade')}</span>
          <span className="font-mono">{formatExpiry(meta.expiryDate)}</span>
        </span>
      )}
      {meta.strikePrice != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-fg-muted bg-bg-elevated border border-border-default">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.strike', 'Strike')}</span>
          <span className="font-mono">{money(meta.strikePrice)}</span>
        </span>
      )}
      {meta.maxLossTry != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-rose-400 bg-rose-500/10 border border-rose-500/30">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.maxLoss', 'Max Kayıp')}</span>
          <span className="font-mono">{money(meta.maxLossTry)}</span>
        </span>
      )}
      {meta.maxGainTry != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-emerald-400 bg-emerald-500/10 border border-emerald-500/30">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.maxGain', 'Max Kazanç')}</span>
          <span className="font-mono">{money(meta.maxGainTry)}</span>
        </span>
      )}
      {meta.currency && meta.currency !== 'TRY' && (
        <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-mono text-fg-muted bg-bg-elevated border border-border-default">
          {meta.currency}
        </span>
      )}
    </div>
  );
}

export default function PositionsTable({ portfolioId, onAssetClick: assetClickProp, onEditClick: editClickProp, onDeleteClick: deleteClickProp, onCloseClick: closeClickProp }) {
  const { t } = useTranslation();
  const listParams = useListParams({ defaultSize: 8, prefix: 'pos' });
  const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`portfolio.positions.sort.${id}`) }));

  const queryParams = {
    ...listParams.params,
    ...(listParams.filter && { assetType: listParams.filter }),
  };

  const { data } = usePortfolioPositions(portfolioId, queryParams);
  const allPositions = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const backfill = useBackfillStatus(portfolioId);
  const elapsed = useElapsedSeconds(backfill.since);

  const [statusFilter, setStatusFilter] = useState('all');
  const positions = allPositions.filter((pos) => {
    if (statusFilter === 'all') return true;
    const isClosed = pos.assetType === 'VIOP'
      && pos.assetName && pos.assetName.includes('KAPALI');
    return statusFilter === 'closed' ? isClosed : !isClosed;
  });

  if (!portfolioId) return null;

  return (
    <PortfolioListShell
      listParams={listParams}
      totalPages={totalPages}
      sortOptions={sortOptions}
      searchPlaceholder={t('portfolio.positions.searchPlaceholder')}
      filterLayoutId="pos-type"
      isEmpty={positions.length === 0}
      emptyIcon={<Package className="h-8 w-8 text-fg-muted" />}
      emptyMessage={listParams.search ? t('portfolio.positions.noSearchResults') : t('portfolio.positions.empty')}
      emptyHint={!listParams.search ? t('portfolio.positions.emptyHint') : undefined}
    >
      <div className="space-y-3">
      <div className="flex items-center gap-1 p-1 rounded-lg bg-bg-elevated border border-border-default w-fit">
        {[
          { id: 'all', label: t('portfolio.positions.statusAll') },
          { id: 'open', label: t('portfolio.positions.statusOpen') },
          { id: 'closed', label: t('portfolio.positions.statusClosed') },
        ].map((opt) => (
          <button
            key={opt.id}
            type="button"
            onClick={() => setStatusFilter(opt.id)}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all border-none cursor-pointer ${
              statusFilter === opt.id
                ? 'bg-accent/15 text-accent'
                : 'bg-transparent text-fg-muted hover:text-fg'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>
      <div className="hidden lg:grid lg:grid-cols-[1.8fr_0.6fr_0.9fr_0.9fr_0.9fr_0.9fr_1.1fr_72px_20px] gap-2 px-4 py-2 text-xs text-fg-muted font-medium">
        <span>{t('portfolio.positions.assetCol')}</span>
        <span className="text-right">{t('portfolio.positions.quantityCol')}</span>
        <span className="text-right">{t('portfolio.positions.entryDateCol')}</span>
        <span className="text-right">{t('portfolio.positions.entryPriceCol')}</span>
        <span className="text-right">{t('portfolio.positions.currentPriceCol')}</span>
        <span className="text-right">{t('portfolio.positions.marketValueCol')}</span>
        <span className="text-right">{t('portfolio.positions.pnlCol')}</span>
        <span className="text-center">{t('portfolio.positions.actionsCol')}</span>
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
          onCloseClick={closeClickProp}
        />
      ))}
      </div>
    </PortfolioListShell>
  );
}

function PositionRow({ pos, pending, elapsed, onAssetClick, onEditClick, onDeleteClick, onCloseClick }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const pnlClass = getChangeClass(pos.pnlTry);
  const localeTag = t('common.localeTag');
  const assetTypeLabel = t(`assets.labels.${pos.assetType}`, { defaultValue: pos.assetType });
  const isDerivative = pos.assetType === 'VIOP';
  const isClosedDerivative = isDerivative && pos.assetName && pos.assetName.includes('KAPALI');
  const derivativeName = isDerivative ? pos.derivative?.displayName : null;
  const displayName = pos.assetName && pos.assetName !== pos.assetCode
    ? pos.assetName.replace(/\s·\sKAPALI$/, '')
    : assetTypeLabel;
  const showEdit = true;
  const showCloseButton = isDerivative && !isClosedDerivative;

  const guard = (fn) => () => { if (!pending && fn) fn(pos); };
  const assetClick = guard(onAssetClick);
  const editClick = isClosedDerivative ? guard(onCloseClick) : guard(onEditClick);
  const deleteClick = guard(onDeleteClick);
  const closeClick = guard(onCloseClick);

  return (
    <Card
      as={motion.div}
      variants={cardVariants}
      variant="elevated"
      radius="2xl"
      padding="none"
      backdropBlur
      interactive={!pending}
      pending={pending}
      className="group"
    >
      {pending && (
        <>
          <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-accent/60 to-transparent animate-pulse z-10" />
          <div className="absolute top-2.5 left-3 right-3 flex items-center justify-between text-[11px] font-medium text-accent z-10 pointer-events-none">
            <div className="flex items-center gap-1.5">
              <Spinner size="xs" tone="inherit" />
              <span className="tracking-tight">{t('portfolio.positions.preparing')}</span>
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
      <div className="hidden lg:grid lg:grid-cols-[1.8fr_0.6fr_0.9fr_0.9fr_0.9fr_0.9fr_1.1fr_72px_20px] gap-2 items-center p-4 min-w-0">
        <div className="flex items-center gap-2.5 cursor-pointer min-w-0" onClick={assetClick}>
          <AssetBadge pos={pos} />
          <div className="min-w-0">
            <div className="flex items-start gap-1.5 flex-wrap">
              <p className={`font-semibold text-fg leading-tight ${isDerivative ? 'text-xs break-all' : 'text-sm truncate'}`}>
                {assetCodeLabel(pos.assetType, pos.assetCode)}
              </p>
              {isDerivative && (
                <span className={`shrink-0 inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide ${
                  isClosedDerivative
                    ? 'bg-warning/15 text-warning border border-warning/30'
                    : 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                }`}>
                  {isClosedDerivative ? t('portfolio.derivatives.closed') : t('portfolio.derivatives.open')}
                </span>
              )}
            </div>
            {!isDerivative && (
              <p className="text-[11px] text-fg-muted truncate">{displayName}</p>
            )}
            {isDerivative && derivativeName && (
              <p className="text-[11px] text-fg-muted truncate">{derivativeName}</p>
            )}
            {isDerivative && pos.derivative && (
              <DerivativeChips meta={pos.derivative} money={money} t={t} localeTag={localeTag} />
            )}
          </div>
        </div>
        <p className="text-right text-[11px] font-mono text-fg truncate">{Number(pos.quantity).toLocaleString(localeTag, { maximumFractionDigits: 6 })}</p>
        <p className="text-right text-[11px] font-mono text-fg-muted truncate">{formatEntryDate(pos.entryDate, localeTag)}</p>
        <p className="text-right text-[11px] font-mono text-fg truncate">{money(pos.entryPrice)}</p>
        <p className="text-right text-[11px] font-mono text-fg truncate">{money(pos.currentPriceTry)}</p>
        <p className="text-right text-[11px] font-mono text-fg truncate">{money(pos.marketValueTry)}</p>
        <div className="text-right min-w-0">
          <p className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]} truncate`}>{money(pos.pnlTry)}</p>
          <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(pos.pnlPercent)}</span>
        </div>
        <div className="flex justify-center gap-1">
          {showEdit && (
            <button onClick={(e) => { e.stopPropagation(); editClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label={t('common.edit')}>
              <Pencil className="h-3 w-3" />
            </button>
          )}
          {showCloseButton && (
            <button onClick={(e) => { e.stopPropagation(); closeClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.derivatives.closeTitle', 'Pozisyon Kapat')}>
              <XCircle className="h-3 w-3" />
            </button>
          )}
          <button onClick={(e) => { e.stopPropagation(); deleteClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label={t('common.delete')}>
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
              <div className="flex items-center gap-1.5">
                <p className="text-sm font-semibold text-fg truncate">{pos.assetCode}</p>
                {isDerivative && (
                  <span className={`shrink-0 inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide ${
                    isClosedDerivative
                      ? 'bg-warning/15 text-warning border border-warning/30'
                      : 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                  }`}>
                    {isClosedDerivative ? t('portfolio.derivatives.closed') : t('portfolio.derivatives.open')}
                  </span>
                )}
              </div>
              {!isDerivative && (
                <p className="text-[11px] text-fg-muted truncate">{displayName}</p>
              )}
              {isDerivative && derivativeName && (
                <p className="text-[11px] text-fg-muted truncate">{derivativeName}</p>
              )}
              {isDerivative && pos.derivative && (
                <DerivativeChips meta={pos.derivative} money={money} t={t} localeTag={localeTag} />
              )}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(pos.pnlPercent)}</span>
            {showEdit && (
              <button onClick={(e) => { e.stopPropagation(); editClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label={t('common.edit')}>
                <Pencil className="h-3 w-3" />
              </button>
            )}
            {showCloseButton && (
              <button onClick={(e) => { e.stopPropagation(); closeClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.derivatives.closeTitle', 'Pozisyon Kapat')}>
                <XCircle className="h-3 w-3" />
              </button>
            )}
            <button onClick={(e) => { e.stopPropagation(); deleteClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label={t('common.delete')}>
              <Trash2 className="h-3 w-3" />
            </button>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.quantityCol')}</p><p className="font-mono text-fg font-medium">{Number(pos.quantity).toLocaleString(localeTag, { maximumFractionDigits: 6 })}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.entryDateCol')}</p><p className="font-mono text-fg font-medium">{formatEntryDate(pos.entryDate, localeTag)}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.entryPriceCol')}</p><p className="font-mono text-fg font-medium">{money(pos.entryPrice)}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.pnlCol')}</p><p className={`font-mono font-semibold ${changeColors[pnlClass]}`}>{money(pos.pnlTry)}</p></div>
        </div>
      </div>
      </div>
    </Card>
  );
}
