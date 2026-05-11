import { useState, useEffect, useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import {
  DndContext, closestCenter, PointerSensor, KeyboardSensor, useSensor, useSensors,
} from '@dnd-kit/core';
import {
  SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates,
  arrayMove, useSortable,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useSearchParams } from 'react-router-dom';
import {
  Eye, AlertCircle, Plus, Trash2, ArrowUp, ArrowDown, TrendingUp, TrendingDown, GripVertical,
  Loader2, Inbox, Star, ListPlus, RotateCcw, Pencil,
} from 'lucide-react';
import PageHeader from '../../shared/components/layout/PageHeader';
import AssetBadge from '../../shared/components/asset/AssetBadge';
import { watchlistName } from '../../shared/utils/watchlistName';
import ConfirmDialog from '../../shared/components/modal/ConfirmDialog';
import useAppStore from '../../shared/stores/useAppStore';
import {
  useWatchlists,
  useWatchlistItems,
  useDeleteWatchlist,
  useRemoveWatchlistItem,
  useReorderWatchlistItems,
} from '../../shared/hooks/useWatchlist';
import SortSelect from '../../shared/components/form/SortSelect';
import { usePriceAlerts, useDeletePriceAlert, useReactivatePriceAlert } from '../../shared/hooks/usePriceAlerts';
import useListParams from '../../shared/hooks/useListParams';
import Pagination from '../../shared/components/form/Pagination';
import AddPriceAlertModal from './components/AddPriceAlertModal';
import AddWatchlistItemModal from './components/AddWatchlistItemModal';
import CreateWatchlistModal from './components/CreateWatchlistModal';
import EditWatchlistItemModal from './components/EditWatchlistItemModal';
import EditPriceAlertModal from './components/EditPriceAlertModal';
import { toast } from '../../shared/components/feedback/Toast';
import { extractApiError } from '../../shared/utils/apiError';
import { formatPriceTRY, formatPercent, getChangeClass, changeColors, changeBg } from '../../shared/utils/formatters';

import { WATCHLIST_SORT_OPTION_IDS, DIRECTION_META, assetRoute } from './lib/watchConstants';
import WatchlistRow from './components/WatchlistRow';
import AlertRow from './components/AlertRow';
function ViewTabs({ view, onChange, watchCount, alertsCount }) {
  const { t } = useTranslation();
  const tabs = [
    { id: 'watchlist', label: t('watch.tabs.watchlist'), Icon: Star, count: watchCount },
    { id: 'alerts', label: t('watch.tabs.alerts'), Icon: AlertCircle, count: alertsCount },
  ];
  return (
    <div className="flex gap-1 rounded-xl border border-border-default bg-bg-elevated p-1 self-start">
      {tabs.map(({ id, label, Icon, count }) => {
        const active = view === id;
        return (
          <button
            key={id}
            type="button"
            onClick={() => onChange(id)}
            className={`relative inline-flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-colors border-none cursor-pointer ${
              active ? 'text-accent' : 'text-fg-muted hover:text-fg'
            } bg-transparent`}
          >
            {active && (
              <motion.span
                layoutId="watch-view-tab"
                className="absolute inset-0 rounded-lg bg-accent/12"
                transition={{ type: 'spring', stiffness: 320, damping: 28 }}
              />
            )}
            <Icon className="relative z-10 h-3.5 w-3.5" />
            <span className="relative z-10">{label}</span>
            {count != null && count > 0 && (
              <span className={`relative z-10 text-[10px] font-mono px-1.5 py-0.5 rounded-md ${
                active ? 'bg-accent/20 text-accent' : 'bg-surface text-fg-subtle'
              }`}>{count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}

function WatchlistTabs({ lists, activeId, onSelect, onCreate, onDelete }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap items-center gap-2">
      {lists.map((list) => {
        const active = list.id === activeId;
        return (
          <div
            key={list.id}
            className={`relative inline-flex items-stretch rounded-lg border transition-colors shrink-0 overflow-hidden ${
              active
                ? 'border-accent/50 bg-accent/10 shadow-accent/20'
                : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-accent/5'
            }`}
          >
            <button
              type="button"
              onClick={() => onSelect(list.id)}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-transparent border-none cursor-pointer min-w-0 ${
                active ? 'text-accent' : 'text-fg-muted hover:text-fg'
              }`}
              title={watchlistName(t, list)}
            >
              {list.isDefault && <Star className="h-3 w-3 text-warning fill-warning shrink-0" />}
              <span className="truncate max-w-[140px]">{watchlistName(t, list)}</span>
              <span className={`text-[10px] font-mono shrink-0 ${active ? 'text-accent/70' : 'text-fg-subtle'}`}>
                {list.itemCount}
              </span>
            </button>
            {!list.isDefault && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(list);
                }}
                className={`flex items-center justify-center px-1.5 border-l shrink-0 bg-transparent cursor-pointer ${
                  active ? 'border-accent/30 text-accent/70 hover:text-danger' : 'border-border-default text-fg-muted hover:text-danger hover:bg-danger/5'
                }`}
                title={t('watch.deleteListTitle')}
              >
                <Trash2 className="h-3 w-3" />
              </button>
            )}
          </div>
        );
      })}
      <button
        type="button"
        onClick={onCreate}
        className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-dashed border-border-default text-fg-muted hover:border-accent hover:text-accent hover:bg-accent/5 transition-colors shrink-0 cursor-pointer bg-transparent"
      >
        <ListPlus className="h-3.5 w-3.5" />
        {t('watch.newListCta')}
      </button>
    </div>
  );
}

export default function WatchPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const view = searchParams.get('view') === 'alerts' ? 'alerts' : 'watchlist';
  const setView = useCallback((next) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      params.set('view', next);
      return params;
    }, { replace: true });
  }, [setSearchParams]);
  const isWatchlist = view === 'watchlist';
  const isAlerts = view === 'alerts';

  const [addItemOpen, setAddItemOpen] = useState(false);
  const [createListOpen, setCreateListOpen] = useState(false);
  const [alertOpen, setAlertOpen] = useState(false);
  const [pendingDeleteList, setPendingDeleteList] = useState(null);
  const [editingItem, setEditingItem] = useState(null);
  const [editingAlert, setEditingAlert] = useState(null);
  const activeListId = useAppStore((s) => s.activeWatchlistId);
  const setActiveListId = useAppStore((s) => s.setActiveWatchlistId);

  const lists = useWatchlists({ enabled: isWatchlist });
  const watchlists = useMemo(() => lists.data ?? [], [lists.data]);

  useEffect(() => {
    if (!isWatchlist || watchlists.length === 0) return;
    const stillExists = activeListId != null && watchlists.some((w) => w.id === activeListId);
    if (!stillExists) {
      const def = watchlists.find((w) => w.isDefault) ?? watchlists[0];
      setActiveListId(def.id);
    }
  }, [isWatchlist, activeListId, watchlists, setActiveListId]);

  const watchParams = useListParams({ defaultDirection: 'asc', defaultSize: 0, prefix: 'watch' });
  const sortBy = watchParams.sort || 'CUSTOM';
  const sortDirection = (watchParams.direction || 'asc').toUpperCase();
  const items = useWatchlistItems(activeListId, { sort: sortBy, direction: sortDirection, enabled: isWatchlist });
  const alertParams = useListParams({ defaultSize: 10, prefix: 'alerts' });
  const alerts = usePriceAlerts({ page: alertParams.page, size: alertParams.size, enabled: isAlerts });
  const removeWatchlistItem = useRemoveWatchlistItem(activeListId);
  const reorderWatchlistItems = useReorderWatchlistItems(activeListId);
  const deletePriceAlert = useDeletePriceAlert();
  const reactivatePriceAlert = useReactivatePriceAlert();
  const deleteWatchlist = useDeleteWatchlist();
  const dndSensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const watchItems = items.data ?? [];
  const watchlistSortOptions = WATCHLIST_SORT_OPTION_IDS.map(id => ({ id, label: t(`watch.sortOptions.${id}`) }));
  const alertItems = alerts.data?.content ?? alerts.data?.items ?? [];
  const alertTotalPages = alerts.data?.totalPages ?? 0;
  const activeList = watchlists.find((w) => w.id === activeListId);

  const handleRemoveWatchlistItem = async (id) => {
    try {
      await removeWatchlistItem.mutateAsync(id);
      toast.success(t('watch.toast.itemRemoved'));
    } catch (err) {
      toast.error(extractApiError(err, t('watch.toast.deleteFailed')));
    }
  };

  const handleDeleteAlert = async (id) => {
    try {
      await deletePriceAlert.mutateAsync(id);
      toast.success(t('watch.toast.alertDeleted'));
    } catch (err) {
      toast.error(extractApiError(err, t('watch.toast.deleteFailed')));
    }
  };

  const handleReactivateAlert = async (id) => {
    try {
      await reactivatePriceAlert.mutateAsync(id);
      toast.success(t('watch.toast.alertReactivated'));
    } catch (err) {
      toast.error(extractApiError(err, t('watch.toast.reactivateFailed')));
    }
  };

  const requestDeleteList = (list) => {
    if (list.isDefault) return;
    setPendingDeleteList(list);
  };

  const confirmDeleteList = async () => {
    const list = pendingDeleteList;
    if (!list) return;
    setPendingDeleteList(null);
    try {
      await deleteWatchlist.mutateAsync(list.id);
      toast.success(t('watch.toast.listDeleted'));
      if (activeListId === list.id) setActiveListId(null);
    } catch (err) {
      toast.error(extractApiError(err, t('watch.toast.deleteFailed')));
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        icon={<Eye className="h-5 w-5" />}
        title={t('watch.headerTitle')}
        onRefresh={() => {
          if (isWatchlist) { lists.refetch(); items.refetch(); }
          else { alerts.refetch(); }
        }}
        loading={isWatchlist
          ? (lists.isFetching || items.isFetching)
          : alerts.isFetching}
      />

      <ViewTabs view={view} onChange={setView} watchCount={watchlists.reduce((acc, w) => acc + (w.itemCount ?? 0), 0)} alertsCount={alerts.data?.totalElements ?? alertItems.length} />

      {isWatchlist && (
      <section className="rounded-xl border border-border-default bg-bg-elevated card-hover">
        <header className="flex items-center justify-between px-4 py-3 border-b border-border-default gap-3">
          <div className="flex items-center gap-2 shrink-0">
            <Star className="h-4 w-4 text-warning" />
            <h2 className="text-sm font-bold text-fg tracking-tight">{t('watch.myLists')}</h2>
          </div>
          <motion.button
            onClick={() => setAddItemOpen(true)}
            disabled={activeListId == null}
            whileHover={{ y: -1 }}
            whileTap={{ scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 400, damping: 25 }}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright shadow-lg shadow-accent/20 transition-colors border-none cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Plus className="h-3.5 w-3.5" />
            {t('watch.addAsset')}
          </motion.button>
        </header>
        <div className="px-4 py-3 border-b border-border-default">
          {lists.isLoading ? (
            <div className="flex items-center gap-2 text-xs text-fg-muted">
              <Loader2 className="h-3.5 w-3.5 animate-spin text-accent" />
              {t('watch.listsLoading')}
            </div>
          ) : (
            <WatchlistTabs
              lists={watchlists}
              activeId={activeListId}
              onSelect={setActiveListId}
              onCreate={() => setCreateListOpen(true)}
              onDelete={requestDeleteList}
            />
          )}
        </div>
        <div className="flex items-center justify-end px-4 py-2 border-b border-border-default">
          <SortSelect
            value={sortBy}
            direction={sortDirection.toLowerCase()}
            options={watchlistSortOptions}
            onSortChange={(id) => {
              const next = id || 'CUSTOM';
              if (next === 'CUSTOM') {
                watchParams.update({ sort: '', dir: '', page: 0 });
              } else {
                watchParams.setSort(next);
              }
            }}
            onDirectionChange={(d) => watchParams.setDirection(d)}
            showDefault={false}
            align="right"
            hideDirection={sortBy === 'CUSTOM'}
          />
        </div>
        <div className="grid grid-cols-[auto_auto_1fr_auto_auto] gap-3 px-4 py-2 border-b border-border-default text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
          <span className="w-6" />
          <span className="w-9">&nbsp;</span>
          <span>{t('watch.headers.asset')}</span>
          <span className="text-right">{t('watch.headers.lastPrice')}</span>
          <span className="min-w-[64px]" />
        </div>
        <div className="flex flex-col">
          {items.isLoading || activeListId == null ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted">
              <Loader2 className="h-4 w-4 animate-spin text-accent" />
              {t('watch.loading')}
            </div>
          ) : watchItems.length === 0 ? (
            <EmptyState
              icon={<Inbox className="h-5 w-5 text-fg-subtle" />}
              title={activeList ? t('watch.emptyList.titleNamed', { name: activeList.name }) : t('watch.emptyList.title')}
              hint={t('watch.emptyList.hint')}
            />
          ) : sortBy === 'CUSTOM' ? (
            <DndContext
              sensors={dndSensors}
              collisionDetection={closestCenter}
              onDragEnd={(e) => {
                const { active, over } = e;
                if (!over || active.id === over.id) return;
                const oldIndex = watchItems.findIndex((it) => it.id === active.id);
                const newIndex = watchItems.findIndex((it) => it.id === over.id);
                if (oldIndex < 0 || newIndex < 0) return;
                const reordered = arrayMove(watchItems, oldIndex, newIndex);
                reorderWatchlistItems.mutate(reordered.map((it) => it.id));
              }}
            >
              <SortableContext items={watchItems.map((it) => it.id)} strategy={verticalListSortingStrategy}>
                {watchItems.map((item) => (
                  <WatchlistRow key={item.id} item={item} onRemove={handleRemoveWatchlistItem} onEdit={setEditingItem} draggable />
                ))}
              </SortableContext>
            </DndContext>
          ) : (
            watchItems.map((item) => (
              <WatchlistRow key={item.id} item={item} onRemove={handleRemoveWatchlistItem} onEdit={setEditingItem} draggable={false} />
            ))
          )}
        </div>
      </section>
      )}

      {isAlerts && (
      <section className="rounded-xl border border-border-default bg-bg-elevated card-hover">
        <header className="flex items-center justify-between px-4 py-3 border-b border-border-default">
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-accent" />
            <h2 className="text-sm font-bold text-fg tracking-tight">{t('watch.tabs.alerts')}</h2>
            <span className="text-[10px] font-mono text-fg-subtle px-1.5 py-0.5 rounded-md bg-surface">
              {alertItems.length}
            </span>
          </div>
          <motion.button
            onClick={() => setAlertOpen(true)}
            whileHover={{ y: -1 }}
            whileTap={{ scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 400, damping: 25 }}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright shadow-lg shadow-accent/20 transition-colors border-none cursor-pointer"
          >
            <Plus className="h-3.5 w-3.5" />
            {t('watch.createAlert')}
          </motion.button>
        </header>
        <div className="grid grid-cols-[auto_1fr_auto_auto_auto_auto] gap-4 px-4 py-2 border-b border-border-default text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
          <span className="w-9">&nbsp;</span>
          <span>{t('watch.headers.asset')}</span>
          <span>{t('watch.headers.direction')}</span>
          <span className="text-right min-w-[90px]">{t('watch.headers.threshold')}</span>
          <span className="min-w-[80px]">{t('watch.headers.status')}</span>
          <span className="min-w-[110px]" />
        </div>
        <div className="divide-y divide-border-default">
          {alerts.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted">
              <Loader2 className="h-4 w-4 animate-spin text-accent" />
              {t('watch.loading')}
            </div>
          ) : alertItems.length === 0 ? (
            <EmptyState
              icon={<Inbox className="h-5 w-5 text-fg-subtle" />}
              title={t('watch.emptyAlerts.title')}
              hint={t('watch.emptyAlerts.hint')}
            />
          ) : (
            alertItems.map((alert) => (
              <AlertRow key={alert.id} alert={alert} onDelete={handleDeleteAlert} onReactivate={handleReactivateAlert} onEdit={setEditingAlert} />
            ))
          )}
        </div>
        <Pagination page={alertParams.page} totalPages={alertTotalPages} onPageChange={alertParams.setPage} />
      </section>
      )}

      <EditWatchlistItemModal
        open={editingItem != null}
        onClose={() => setEditingItem(null)}
        item={editingItem}
        watchlistId={activeListId}
      />
      <EditPriceAlertModal
        open={editingAlert != null}
        onClose={() => setEditingAlert(null)}
        alert={editingAlert}
      />
      <AddPriceAlertModal isOpen={alertOpen} onClose={() => setAlertOpen(false)} />
      <AddWatchlistItemModal
        isOpen={addItemOpen}
        onClose={() => setAddItemOpen(false)}
        watchlistId={activeListId}
      />
      <CreateWatchlistModal
        isOpen={createListOpen}
        onClose={() => setCreateListOpen(false)}
        onCreated={(list) => setActiveListId(list.id)}
      />
      <ConfirmDialog
        open={pendingDeleteList != null}
        title={t('watch.deleteListConfirm.title')}
        message={pendingDeleteList ? t('watch.deleteListConfirm.message', { name: pendingDeleteList.name }) : ''}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        variant="danger"
        loading={deleteWatchlist.isPending}
        onConfirm={confirmDeleteList}
        onCancel={() => setPendingDeleteList(null)}
      />
    </div>
  );
}

function EmptyState({ icon, title, hint }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
      className="flex flex-col items-center justify-center gap-2 py-14 px-4 text-center"
    >
      <div className="relative mb-2">
        <div className="absolute inset-0 rounded-2xl bg-accent/15 blur-xl -z-10" />
        <motion.div
          animate={{ y: [0, -3, 0] }}
          transition={{ duration: 3.5, repeat: Infinity, ease: 'easeInOut' }}
          className="flex items-center justify-center w-12 h-12 rounded-2xl bg-gradient-to-br from-accent/15 to-accent-secondary/10 border border-accent/20"
        >
          {icon}
        </motion.div>
      </div>
      <p className="text-sm font-semibold text-fg">{title}</p>
      <p className="text-[11px] text-fg-subtle max-w-xs leading-relaxed">{hint}</p>
    </motion.div>
  );
}
