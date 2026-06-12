import { useState, useEffect, useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import {
  DndContext, closestCenter, PointerSensor, KeyboardSensor, useSensor, useSensors,
} from '@dnd-kit/core';
import {
  SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates,
  arrayMove,
} from '@dnd-kit/sortable';
import { useSearchParams } from 'react-router-dom';
import {
  Eye, AlertCircle, Plus,
  Inbox, Star,
} from 'lucide-react';
import PageHeader from '../../shared/components/layout/PageHeader';
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
import Card from '../../shared/components/card';
import Spinner from '../../shared/components/feedback/Spinner';
import { usePriceAlerts, useDeletePriceAlert, useReactivatePriceAlert } from '../../shared/hooks/usePriceAlerts';
import useListParams from '../../shared/hooks/useListParams';
import Pagination from '../../shared/components/form/Pagination';
import AddPriceAlertModal from './components/AddPriceAlertModal';
import AddWatchlistItemModal from './components/AddWatchlistItemModal';
import CreateWatchlistModal from './components/CreateWatchlistModal';
import EditWatchlistItemModal from './components/EditWatchlistItemModal';
import EditPriceAlertModal from './components/EditPriceAlertModal';
import { toast } from '../../shared/components/feedback/toastBus';
import { toastApiError } from '../../shared/utils/apiError';
import { WATCHLIST_SORT_OPTION_IDS } from './lib/watchConstants';
import WatchlistRow from './components/WatchlistRow';
import AlertRow from './components/AlertRow';
import WatchViewTabs from './components/WatchViewTabs';
import WatchlistTabsBar from './components/WatchlistTabsBar';

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
      toastApiError(err, t('watch.toast.deleteFailed'));
    }
  };

  const handleDeleteAlert = async (id) => {
    try {
      await deletePriceAlert.mutateAsync(id);
      toast.success(t('watch.toast.alertDeleted'));
    } catch (err) {
      toastApiError(err, t('watch.toast.deleteFailed'));
    }
  };

  const handleReactivateAlert = async (id) => {
    try {
      await reactivatePriceAlert.mutateAsync(id);
      toast.success(t('watch.toast.alertReactivated'));
    } catch (err) {
      toastApiError(err, t('watch.toast.reactivateFailed'));
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
      toastApiError(err, t('watch.toast.deleteFailed'));
    }
  };

  return (
    <div className="space-y-5" data-tour="watchlist-main">
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

      <WatchViewTabs view={view} onChange={setView} watchCount={watchlists.reduce((acc, w) => acc + (w.itemCount ?? 0), 0)} alertsCount={alerts.data?.totalElements ?? alertItems.length} />

      {isWatchlist && (
      <Card as="section" variant="elevated" radius="xl" padding="none" interactive clip={false}>
        <header className="flex items-center justify-between px-3 sm:px-4 py-3 border-b border-border-default gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <Star className="h-4 w-4 text-warning shrink-0" />
            <h2 className="text-sm font-bold text-fg tracking-tight truncate">{t('watch.myLists')}</h2>
          </div>
          <motion.button
            onClick={() => setAddItemOpen(true)}
            disabled={activeListId == null}
            whileHover={{ y: -1 }}
            whileTap={{ scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 400, damping: 25 }}
            aria-label={t('watch.addAsset')}
            className="inline-flex items-center gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright shadow-lg shadow-accent/20 transition-colors border-none cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed shrink-0"
          >
            <Plus className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{t('watch.addAsset')}</span>
          </motion.button>
        </header>
        <div className="px-4 py-3 border-b border-border-default">
          {lists.isLoading ? (
            <div className="flex items-center gap-2 text-xs text-fg-muted">
              <Spinner size="sm" tone="accent" />
              {t('watch.listsLoading')}
            </div>
          ) : (
            <WatchlistTabsBar
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
        <div className="hidden sm:grid grid-cols-[auto_auto_1fr_auto_auto] gap-3 px-4 py-2 border-b border-border-default text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
          <span className="w-6" />
          <span className="w-9">&nbsp;</span>
          <span>{t('watch.headers.asset')}</span>
          <span className="text-right">{t('watch.headers.lastPrice')}</span>
          <span className="min-w-[64px]" />
        </div>
        <div className="flex flex-col">
          {items.isLoading || activeListId == null ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted">
              <Spinner size="sm" tone="accent" />
              {t('watch.loading')}
            </div>
          ) : watchItems.length === 0 ? (
            <EmptyState
              icon={<Inbox className="h-5 w-5 text-fg-subtle" />}
              title={activeList ? t('watch.emptyList.titleNamed', { name: watchlistName(t, activeList) }) : t('watch.emptyList.title')}
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
      </Card>
      )}

      {isAlerts && (
      <Card as="section" variant="elevated" radius="xl" padding="none" interactive>
        <header className="flex items-center justify-between px-3 sm:px-4 py-3 border-b border-border-default gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <AlertCircle className="h-4 w-4 text-accent shrink-0" />
            <h2 className="text-sm font-bold text-fg tracking-tight truncate">{t('watch.tabs.alerts')}</h2>
            <span className="text-[10px] font-mono text-fg-subtle px-1.5 py-0.5 rounded-md bg-surface shrink-0">
              {alertItems.length}
            </span>
          </div>
          <motion.button
            onClick={() => setAlertOpen(true)}
            whileHover={{ y: -1 }}
            whileTap={{ scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 400, damping: 25 }}
            aria-label={t('watch.createAlert')}
            className="inline-flex items-center gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright shadow-lg shadow-accent/20 transition-colors border-none cursor-pointer shrink-0"
          >
            <Plus className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{t('watch.createAlert')}</span>
          </motion.button>
        </header>
        <div className="hidden md:grid grid-cols-[auto_1fr_auto_auto_auto_auto] gap-4 px-4 py-2 border-b border-border-default text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
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
              <Spinner size="sm" tone="accent" />
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
      </Card>
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
