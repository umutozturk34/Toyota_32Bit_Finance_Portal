import { useCallback, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Package } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { portfolioService } from '../services/portfolioService';
import { isLotPending, useBackfillStatus, usePortfolioPositions, useReopenPosition } from '../hooks/usePortfolioData';
import { useReopenDerivativePosition } from '../hooks/useDerivativePositions';
import { usePositionSelection } from '../hooks/usePositionSelection';
import useListParams from '../../../shared/hooks/useListParams';
import useElapsedSeconds from '../../../shared/hooks/useElapsedSeconds';
import PortfolioListShell from './PortfolioListShell';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import BulkSelectionBar from './BulkSelectionBar';
import BulkDeleteDialog from './BulkDeleteDialog';
import PositionRow from './PositionRow';
import { SORT_OPTION_IDS } from '../lib/positionsTableHelpers';

export default function PositionsTable({ portfolioId, backfill: backfillProp, onAssetClick: assetClickProp, onEditClick: editClickProp, onDeleteClick: deleteClickProp, onCloseClick: closeClickProp, onSellClick: sellClickProp }) {
  const { t } = useTranslation();
  const listParams = useListParams({ defaultSize: 8, prefix: 'pos' });
  const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`portfolio.positions.sort.${id}`) }));

  const [searchParams, setSearchParams] = useSearchParams();
  const statusFilter = searchParams.get('status') || 'all';
  const setStatusFilter = (next) => setSearchParams((prev) => {
    const sp = new URLSearchParams(prev);
    if (next === 'all') sp.delete('status'); else sp.set('status', next);
    return sp;
  }, { replace: true });

  // Memoised so the cross-page select-all callback's dependency array is stable; otherwise the
  // object identity would churn every render and tear down the useCallback.
  const queryParams = useMemo(() => ({
    ...listParams.params,
    ...(listParams.filter && { assetType: listParams.filter }),
    // Status filter pushed to the server: client-side filtering only sees the
    // current page, which hides closed lots when they fall onto later pages.
    ...(statusFilter === 'closed' && { closed: true }),
    ...(statusFilter === 'open' && { closed: false }),
  }), [listParams.params, listParams.filter, statusFilter]);

  const { data } = usePortfolioPositions(portfolioId, queryParams);
  const positions = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;
  const ownBackfill = useBackfillStatus(backfillProp ? null : portfolioId);
  const backfill = backfillProp ?? ownBackfill;
  const elapsed = useElapsedSeconds(backfill.since);

  const reopenMutation = useReopenPosition(portfolioId);
  const reopenDerivativeMutation = useReopenDerivativePosition(portfolioId);
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);
  const [selectingAll, setSelectingAll] = useState(false);

  const selection = usePositionSelection(positions);

  // Pulls every page of positions (respecting the active filters / status) so a single click
  // can select every lot in the portfolio for a bulk delete. Iterates instead of asking for
  // size=10000 because the backend caps {@code max-size} at 100 — the loop honours that and
  // still gives us the complete id list without exotic backend changes.
  const handleSelectAcrossPages = useCallback(async () => {
    if (!portfolioId || selectingAll) return;
    setSelectingAll(true);
    try {
      const all = [];
      const pageSize = 100;
      const maxPages = 50;
      for (let page = 0; page < maxPages; page += 1) {
        const resp = await portfolioService.getPositions(portfolioId, {
          ...queryParams,
          page,
          size: pageSize,
        });
        const rows = resp?.content || [];
        all.push(...rows);
        const reportedTotal = resp?.totalPages ?? 1;
        if (rows.length === 0 || page + 1 >= reportedTotal) break;
      }
      selection.replaceWith(all.map((p) => ({ id: p.id, assetType: p.assetType })));
    } finally {
      setSelectingAll(false);
    }
  }, [portfolioId, queryParams, selection, selectingAll]);

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
        totalAcrossPages={totalElements}
        allSelected={selection.allSelected}
        onClear={selection.clear}
        onToggleAll={selection.toggleAll}
        onSelectAcrossPages={handleSelectAcrossPages}
        onDeleteClick={() => setBulkDeleteOpen(true)}
        isDeleting={false}
        isSelectingAll={selectingAll}
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
        positions={bulkDeleteOpen ? selection.selectedItems : []}
        onClose={() => setBulkDeleteOpen(false)}
        onComplete={() => selection.clear()}
      />
    </PortfolioListShell>
  );
}
