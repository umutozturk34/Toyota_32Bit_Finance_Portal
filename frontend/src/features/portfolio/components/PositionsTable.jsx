import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ChevronRight, Package, Pencil, Trash2, XCircle, ShoppingBag, RotateCcw } from 'lucide-react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { cardVariants } from '../../../shared/utils/animations';
import { formatPercent, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { assetCodeLabel } from '../../../shared/utils/assetCode';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import { isLotPending, useBackfillStatus, usePortfolioPositions, useReopenPosition } from '../hooks/usePortfolioData';
import { useReopenDerivativePosition } from '../hooks/useDerivativePositions';
import { usePositionSelection } from '../hooks/usePositionSelection';
import useListParams from '../../../shared/hooks/useListParams';
import useElapsedSeconds from '../../../shared/hooks/useElapsedSeconds';
import PortfolioListShell from './PortfolioListShell';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import PositionStatusBadge from './PositionStatusBadge';
import PositionAssetBadge from './PositionAssetBadge';
import PositionDerivativeChips from './PositionDerivativeChips';
import SelectableCheckbox from './SelectableCheckbox';
import BulkSelectionBar from './BulkSelectionBar';
import BulkDeleteDialog from './BulkDeleteDialog';

const SORT_OPTION_IDS = ['currentValue', 'profitPercent', 'profitAmount', 'entryDate', 'assetCode', 'quantity'];

function formatEntryDate(dateStr, localeTag) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: '2-digit' });
}

export default function PositionsTable({ portfolioId, backfill: backfillProp, onAssetClick: assetClickProp, onEditClick: editClickProp, onDeleteClick: deleteClickProp, onCloseClick: closeClickProp, onSellClick: sellClickProp }) {
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
  const ownBackfill = useBackfillStatus(backfillProp ? null : portfolioId);
  const backfill = backfillProp ?? ownBackfill;
  const elapsed = useElapsedSeconds(backfill.since);

  const reopenMutation = useReopenPosition(portfolioId);
  const reopenDerivativeMutation = useReopenDerivativePosition(portfolioId);
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);

  const [searchParams, setSearchParams] = useSearchParams();
  const statusFilter = searchParams.get('status') || 'all';
  const setStatusFilter = (next) => setSearchParams((prev) => {
    const sp = new URLSearchParams(prev);
    if (next === 'all') sp.delete('status'); else sp.set('status', next);
    return sp;
  }, { replace: true });
  const isPositionClosed = (pos) => {
    if (pos.assetType === 'VIOP') {
      return pos.assetName && pos.assetName.includes('KAPALI');
    }
    return !!pos.exitDate;
  };
  const positions = allPositions.filter((pos) => {
    if (statusFilter === 'all') return true;
    const closed = isPositionClosed(pos);
    return statusFilter === 'closed' ? closed : !closed;
  });

  const selection = usePositionSelection(positions);

  if (!portfolioId) return null;

  const statusFilterBar = (
    <FilterTabs
      items={[
        { type: 'open', label: t('portfolio.positions.statusOpen') },
        { type: 'closed', label: t('portfolio.positions.statusClosed') },
      ]}
      activeId={statusFilter === 'all' ? 'ALL' : statusFilter}
      onSelect={(id) => setStatusFilter(id === 'ALL' ? 'all' : id)}
      allLabel={t('portfolio.positions.statusAll')}
      showAll
      layoutId="pos-status"
    />
  );

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
      secondaryFilters={statusFilterBar}
    >
      <div className="space-y-3">
      <BulkSelectionBar
        count={selection.count}
        total={positions.length}
        allSelected={selection.allSelected}
        onClear={selection.clear}
        onToggleAll={selection.toggleAll}
        onDeleteClick={() => setBulkDeleteOpen(true)}
        isDeleting={false}
      />
      <div className="hidden lg:grid lg:grid-cols-[28px_minmax(220px,2.4fr)_56px_92px_92px_72px_84px_84px_104px_112px_104px_24px] gap-3 px-4 py-2 text-[10px] text-fg-muted font-medium uppercase tracking-wider whitespace-nowrap">
        <span />
        <span>{t('portfolio.positions.assetCol')}</span>
        <span>{t('portfolio.positions.quantityCol')}</span>
        <span>{t('portfolio.positions.entryDateCol')}</span>
        <span>{t('portfolio.positions.exitDateLabel')}</span>
        <span>{t('portfolio.positions.statusCol')}</span>
        <span>{t('portfolio.positions.entryPriceCol')}</span>
        <span>{t('portfolio.positions.currentPriceCol')}</span>
        <span>{t('portfolio.positions.marketValueCol')}</span>
        <span>{t('portfolio.positions.pnlCol')}</span>
        <span>{t('portfolio.positions.actionsCol')}</span>
        <span />
      </div>

      {positions.map((pos) => (
        <PositionRow
          key={pos.id}
          pos={pos}
          pending={isLotPending(backfill, pos.assetType, pos.assetCode)}
          elapsed={elapsed}
          selected={selection.isSelected(pos.id)}
          onToggleSelect={(e) => selection.toggle(pos.id, e)}
          onAssetClick={assetClickProp}
          onEditClick={editClickProp}
          onDeleteClick={deleteClickProp}
          onCloseClick={closeClickProp}
          onSellClick={sellClickProp}
          onReopenClick={(p) => (p.assetType === 'VIOP' ? reopenDerivativeMutation : reopenMutation).mutate(p.id)}
        />
      ))}
      </div>
      <BulkDeleteDialog
        portfolioId={portfolioId}
        positions={bulkDeleteOpen ? selection.selectedArray : []}
        onClose={() => setBulkDeleteOpen(false)}
        onComplete={() => selection.clear()}
      />
    </PortfolioListShell>
  );
}

function PositionRow({ pos, pending, elapsed, selected, onToggleSelect, onAssetClick, onEditClick, onDeleteClick, onCloseClick, onSellClick, onReopenClick }) {
  const { t } = useTranslation();
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000);
  const pnlClass = getChangeClass(pos.pnlTry);
  const localeTag = t('common.localeTag');
  const assetTypeLabel = t(`assets.labels.${pos.assetType}`, { defaultValue: pos.assetType });
  const isDerivative = pos.assetType === 'VIOP';
  const isClosedDerivative = isDerivative && pos.assetName && pos.assetName.includes('KAPALI');
  const isClosedSpot = !isDerivative && !!pos.exitDate;
  const derivativeName = isDerivative ? pos.derivative?.displayName : null;
  const displayName = pos.assetName && pos.assetName !== pos.assetCode
    ? pos.assetName.replace(/\s·\sKAPALI$/, '')
    : assetTypeLabel;
  const showEdit = !isClosedSpot;
  const showCloseButton = isDerivative && !isClosedDerivative;
  const showSellButton = !isDerivative && !isClosedSpot;
  const showReopenButton = isClosedSpot || isClosedDerivative;

  const guard = (fn) => () => { if (!pending && fn) fn(pos); };
  const assetClick = guard(onAssetClick);
  const editClick = isClosedDerivative ? guard(onCloseClick) : guard(onEditClick);
  const deleteClick = guard(onDeleteClick);
  const closeClick = guard(onCloseClick);
  const sellClick = guard(onSellClick);
  const reopenClick = guard(onReopenClick);

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
      <div className="hidden lg:grid lg:grid-cols-[28px_minmax(220px,2.4fr)_56px_92px_92px_72px_84px_84px_104px_112px_104px_24px] gap-3 items-start p-4 min-w-0">
        <div className="flex items-center justify-center pt-1">
          {!pending && <SelectableCheckbox checked={!!selected} onClick={onToggleSelect} label={pos.assetCode} />}
        </div>
        <div className="flex items-center gap-2.5 cursor-pointer min-w-0" onClick={assetClick}>
          <PositionAssetBadge pos={pos} />
          <div className="min-w-0">
            <div className="flex items-start gap-1.5 flex-wrap">
              <p className={`font-semibold text-fg leading-tight ${isDerivative ? 'text-xs break-all' : 'text-sm truncate'}`}>
                {assetCodeLabel(pos.assetType, pos.assetCode)}
              </p>
            </div>
            {!isDerivative && (
              <p className="text-[11px] text-fg-muted truncate">{displayName}</p>
            )}
            {isDerivative && derivativeName && (
              <p className="text-[11px] text-fg-muted truncate">{derivativeName}</p>
            )}
            {isDerivative && pos.derivative && (
              <PositionDerivativeChips meta={pos.derivative} money={money} t={t} localeTag={localeTag} />
            )}
          </div>
        </div>
        <p className="text-left text-[11px] font-mono text-fg truncate">{Number(pos.quantity).toLocaleString(localeTag, { maximumFractionDigits: 6 })}</p>
        <p className="text-left text-[11px] font-mono text-fg-muted truncate">{formatEntryDate(pos.entryDate, localeTag)}</p>
        <p className="text-left text-[11px] font-mono text-fg-muted truncate">
          {isClosedSpot || isClosedDerivative ? formatEntryDate(pos.exitDate, localeTag) || '—' : '—'}
        </p>
        <div className="flex justify-start">
          <PositionStatusBadge closed={isClosedSpot || isClosedDerivative} isDerivative={isDerivative} />
        </div>
        <p className="text-left text-[11px] font-mono text-fg truncate">{money(pos.entryPrice)}</p>
        <p className={`text-left text-[11px] font-mono truncate ${isClosedSpot || isClosedDerivative ? 'text-fg-muted italic' : 'text-fg'}`}>{money(pos.currentPriceTry)}</p>
        <p className={`text-left text-[11px] font-mono truncate ${isClosedSpot || isClosedDerivative ? 'text-fg-muted italic' : 'text-fg'}`} title={money(pos.marketValueTry)}>{bigMoney(pos.marketValueTry)}</p>
        <div className="text-left min-w-0">
          <p className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]} truncate`} title={money(pos.pnlTry)}>{bigMoney(pos.pnlTry)}</p>
          <div className="flex items-center gap-1 flex-wrap">
            <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(pos.pnlPercent)}</span>
            {pos.realPnlPercent != null && (
              <span className={`inline-flex items-center text-[9px] font-mono tabular-nums tracking-[0.04em] uppercase ${Number(pos.realPnlPercent) >= 0 ? 'text-emerald-500' : 'text-red-500'}`} title={t('portfolio.positions.realReturnTooltip')}>
                {t('portfolio.positions.realReturnAbbr')} {formatPercent(pos.realPnlPercent)}
              </span>
            )}
          </div>
        </div>
        <div className="flex justify-start gap-1">
          {showEdit && (
            <button onClick={(e) => { e.stopPropagation(); editClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label={t('common.edit')}>
              <Pencil className="h-3 w-3" />
            </button>
          )}
          {showSellButton && (
            <button onClick={(e) => { e.stopPropagation(); sellClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.sell.title', 'Sell')}>
              <ShoppingBag className="h-3 w-3" />
            </button>
          )}
          {showReopenButton && (
            <button onClick={(e) => { e.stopPropagation(); reopenClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.reopen.title', 'Reopen')}>
              <RotateCcw className="h-3 w-3" />
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
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0 flex-1" onClick={assetClick}>
            {!pending && <SelectableCheckbox checked={!!selected} onClick={onToggleSelect} label={pos.assetCode} />}
            <PositionAssetBadge pos={pos} />
            <div className="min-w-0">
              <div className="flex items-center gap-1.5">
                <p className="text-sm font-semibold text-fg truncate">{pos.assetCode}</p>
                <PositionStatusBadge closed={isClosedSpot || isClosedDerivative} isDerivative={isDerivative} />
              </div>
              {!isDerivative && (
                <p className="text-[11px] text-fg-muted truncate">{displayName}</p>
              )}
              {isDerivative && derivativeName && (
                <p className="text-[11px] text-fg-muted truncate">{derivativeName}</p>
              )}
              {isDerivative && pos.derivative && (
                <PositionDerivativeChips meta={pos.derivative} money={money} t={t} localeTag={localeTag} />
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
            {showSellButton && (
              <button onClick={(e) => { e.stopPropagation(); sellClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.sell.title', 'Sell')}>
                <ShoppingBag className="h-3 w-3" />
              </button>
            )}
            {showReopenButton && (
              <button onClick={(e) => { e.stopPropagation(); reopenClick(); }} className="flex items-center justify-center w-7 h-7 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.reopen.title', 'Reopen')}>
                <RotateCcw className="h-3 w-3" />
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
          {(isClosedSpot || isClosedDerivative) && (
            <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.exitDateLabel')}</p><p className="font-mono text-fg font-medium">{formatEntryDate(pos.exitDate, localeTag) || '—'}</p></div>
          )}
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.entryPriceCol')}</p><p className="font-mono text-fg font-medium">{money(pos.entryPrice)}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.pnlCol')}</p><p className={`font-mono font-semibold ${changeColors[pnlClass]}`} title={money(pos.pnlTry)}>{bigMoney(pos.pnlTry)}</p></div>
        </div>
      </div>
      </div>
    </Card>
  );
}
