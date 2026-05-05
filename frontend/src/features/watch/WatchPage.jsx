import { useState, useEffect, useMemo } from 'react';
import {
  DndContext, closestCenter, PointerSensor, KeyboardSensor, useSensor, useSensors,
} from '@dnd-kit/core';
import {
  SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates,
  arrayMove, useSortable,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import {
  Eye, AlertCircle, Plus, Trash2, ArrowUp, ArrowDown, TrendingUp, TrendingDown, GripVertical,
  Loader2, Inbox, Star, ListPlus, RotateCcw,
} from 'lucide-react';
import PageHeader from '../../shared/components/PageHeader';
import AssetBadge from '../../shared/components/AssetBadge';
import ConfirmDialog from '../../shared/components/ConfirmDialog';
import useAppStore from '../../shared/stores/useAppStore';
import { useAssetDetailPrefetch } from '../../shared/hooks/useAssetDetailPrefetch';
import {
  useWatchlists,
  useWatchlistItems,
  useDeleteWatchlist,
  useRemoveWatchlistItem,
  useReorderWatchlistItems,
} from '../../shared/hooks/useWatchlist';
import SortSelect from '../../shared/components/SortSelect';
import { usePriceAlerts, useDeletePriceAlert, useReactivatePriceAlert } from '../../shared/hooks/usePriceAlerts';
import useListParams from '../../shared/hooks/useListParams';
import Pagination from '../../shared/components/Pagination';
import AddPriceAlertModal from './AddPriceAlertModal';
import AddWatchlistItemModal from './AddWatchlistItemModal';
import CreateWatchlistModal from './CreateWatchlistModal';
import { toast } from '../../shared/components/Toast';
import { extractApiError } from '../../shared/utils/apiError';
import { formatPriceTRY, formatPercent, getChangeClass, changeColors, changeBg } from '../../shared/utils/formatters';

const WATCHLIST_SORT_OPTIONS = [
  { id: '', label: 'Sıralamam' },
  { id: 'NAME', label: 'Alfabetik' },
  { id: 'CURRENT_PRICE', label: 'Fiyat' },
  { id: 'CHANGE_PERCENT', label: '% Değişim' },
  { id: 'ADDED_AT', label: 'Eklenme tarihi' },
];

const DIRECTION_META = {
  ABOVE: { label: 'üstüne', Icon: ArrowUp, tint: 'text-success' },
  BELOW: { label: 'altına', Icon: ArrowDown, tint: 'text-danger' },
  CHANGE_PCT_UP: { label: '% yükseliş', Icon: TrendingUp, tint: 'text-success' },
  CHANGE_PCT_DOWN: { label: '% düşüş', Icon: TrendingDown, tint: 'text-danger' },
};

const ROUTE_BY_TYPE = {
  CRYPTO: (code) => `/crypto/${code}`,
  STOCK: (code) => `/stocks/${code}`,
  FOREX: (code) => `/forex/${code}`,
  FUND: (code) => `/funds/${code}`,
  COMMODITY: (code) => `/commodities/${code}`,
  BOND: () => '/bonds',
};

function assetRoute(marketType, assetCode) {
  const builder = ROUTE_BY_TYPE[marketType];
  return builder ? builder(assetCode) : null;
}

function WatchlistTabs({ lists, activeId, onSelect, onCreate, onDelete }) {
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
              title={list.name}
            >
              {list.isDefault && <Star className="h-3 w-3 text-warning fill-warning shrink-0" />}
              <span className="truncate max-w-[140px]">{list.name}</span>
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
                title="Listeyi sil"
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
        Yeni liste
      </button>
    </div>
  );
}

function WatchlistRow({ item, onRemove, draggable }) {
  const navigate = useNavigate();
  const route = assetRoute(item.marketType, item.assetCode);
  const prefetch = useAssetDetailPrefetch();
  const triggerPrefetch = () => prefetch(item.marketType, item.assetCode);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
    disabled: !draggable,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 30 : 'auto',
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      onClick={route ? () => navigate(route) : undefined}
      onMouseEnter={triggerPrefetch}
      onFocus={triggerPrefetch}
      className={`relative group grid grid-cols-[auto_auto_1fr_auto_auto] gap-3 items-center px-4 py-3 transition-[background,box-shadow,border-color] ${
        route ? 'cursor-pointer hover:bg-accent/5' : ''
      } ${isDragging
        ? 'bg-accent/8 shadow-[0_18px_40px_-18px_rgba(99,102,241,0.55),inset_0_0_0_1px_rgba(99,102,241,0.4)] rounded-lg'
        : ''}`}
    >
      {draggable ? (
        <button
          type="button"
          {...attributes}
          {...listeners}
          onClick={(e) => e.stopPropagation()}
          className={`flex items-center justify-center w-6 h-6 cursor-grab active:cursor-grabbing bg-transparent border-none touch-none transition-colors ${
            isDragging ? 'text-accent' : 'text-fg-subtle hover:text-fg'
          }`}
          title="Sürükleyerek sırala"
        >
          <GripVertical className="h-4 w-4" />
        </button>
      ) : (
        <span className="w-6" />
      )}
      <AssetBadge
        assetType={item.marketType}
        assetCode={item.assetCode}
        assetImage={item.image}
        size="md"
      />
      <div className="min-w-0">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-sm font-semibold text-fg truncate group-hover:text-accent transition-colors">
            {item.assetName || item.assetCode}
          </span>
          {item.deltaThreshold != null && (
            <span className="text-[10px] font-mono text-accent shrink-0">±{item.deltaThreshold}%</span>
          )}
        </div>
        <div className="flex items-center gap-2 text-[11px] text-fg-muted">
          <span className="font-mono">{item.assetCode}</span>
          {item.note && (
            <>
              <span className="text-fg-subtle">·</span>
              <span className="truncate">{item.note}</span>
            </>
          )}
        </div>
      </div>
      <div className="text-right">
        <div className="text-sm font-mono font-semibold text-fg tabular-nums">
          {item.currentPrice != null ? formatPriceTRY(item.currentPrice) : '—'}
        </div>
        {item.changePercent != null && (() => {
          const cls = getChangeClass(item.changePercent);
          const isUp = item.changePercent > 0;
          const isDown = item.changePercent < 0;
          const ChangeIcon = isUp ? TrendingUp : isDown ? TrendingDown : null;
          return (
            <div className={`mt-0.5 inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-mono font-semibold tabular-nums ${changeBg[cls]} ${changeColors[cls]}`}>
              {ChangeIcon && <ChangeIcon className="h-3 w-3" />}
              <span>{formatPercent(item.changePercent)}</span>
              {item.changeAmount != null && (
                <span className="font-normal opacity-70">
                  ({isUp ? '+' : ''}{Number(item.changeAmount).toLocaleString('tr-TR', { maximumFractionDigits: 2 })})
                </span>
              )}
            </div>
          );
        })()}
      </div>
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          onRemove(item.id);
        }}
        className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-danger hover:bg-danger/5 bg-transparent border-none cursor-pointer"
        title="Listeden çıkar"
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}

function AlertRow({ alert, onDelete, onReactivate }) {
  const navigate = useNavigate();
  const dir = DIRECTION_META[alert.direction] ?? DIRECTION_META.ABOVE;
  const { Icon, tint } = dir;
  const isFired = !alert.active && alert.triggeredAt;
  const route = assetRoute(alert.marketType, alert.assetCode);
  const prefetch = useAssetDetailPrefetch();
  const triggerPrefetch = () => prefetch(alert.marketType, alert.assetCode);

  return (
    <div
      onClick={route ? () => navigate(route) : undefined}
      onMouseEnter={triggerPrefetch}
      onFocus={triggerPrefetch}
      className={`group grid grid-cols-[auto_1fr_auto_auto_auto_auto] gap-4 items-center px-4 py-3 transition-colors ${
        route ? 'cursor-pointer hover:bg-accent/5' : ''
      } ${isFired ? 'opacity-60' : ''}`}
    >
      <AssetBadge
        assetType={alert.marketType}
        assetCode={alert.assetCode}
        assetImage={alert.image}
        size="md"
      />
      <div className="min-w-0">
        <div className="text-sm font-semibold text-fg truncate group-hover:text-accent transition-colors">
          {alert.assetName || alert.assetCode}
        </div>
        <div className="text-[11px] text-fg-muted font-mono">{alert.assetCode}</div>
      </div>
      <div className={`flex items-center gap-1 text-[11px] font-medium ${tint}`}>
        <Icon className="h-3.5 w-3.5" />
        <span>{dir.label}</span>
      </div>
      <span className="text-sm font-mono font-semibold text-fg tabular-nums min-w-[90px] text-right">
        ₺{Number(alert.threshold).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
      </span>
      <span className={`text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded shrink-0 ${
        isFired ? 'bg-fg-subtle/10 text-fg-subtle' : 'bg-success/10 text-success'
      }`}>
        {isFired ? 'tetiklendi' : 'aktif'}
      </span>
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {isFired && (
          <button
            type="button"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onReactivate(alert.id);
            }}
            className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-accent hover:bg-accent/10 bg-transparent border-none cursor-pointer"
            title="Yeniden aktifleştir"
          >
            <RotateCcw className="h-4 w-4" />
          </button>
        )}
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onDelete(alert.id);
          }}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-danger hover:bg-danger/5 bg-transparent border-none cursor-pointer"
          title="Sil"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

export default function WatchPage() {
  const [addItemOpen, setAddItemOpen] = useState(false);
  const [createListOpen, setCreateListOpen] = useState(false);
  const [alertOpen, setAlertOpen] = useState(false);
  const [pendingDeleteList, setPendingDeleteList] = useState(null);
  const activeListId = useAppStore((s) => s.activeWatchlistId);
  const setActiveListId = useAppStore((s) => s.setActiveWatchlistId);

  const lists = useWatchlists();
  const watchlists = useMemo(() => lists.data ?? [], [lists.data]);

  useEffect(() => {
    if (watchlists.length === 0) return;
    const stillExists = activeListId != null && watchlists.some((w) => w.id === activeListId);
    if (!stillExists) {
      const def = watchlists.find((w) => w.isDefault) ?? watchlists[0];
      setActiveListId(def.id);
    }
  }, [activeListId, watchlists, setActiveListId]);

  const watchParams = useListParams({ defaultDirection: 'asc', defaultSize: 0, prefix: 'watch' });
  const sortBy = watchParams.sort || 'CUSTOM';
  const sortDirection = (watchParams.direction || 'asc').toUpperCase();
  const items = useWatchlistItems(activeListId, { sort: sortBy, direction: sortDirection });
  const alertParams = useListParams({ defaultSize: 10, prefix: 'alerts' });
  const alerts = usePriceAlerts({ page: alertParams.page, size: alertParams.size });
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
  const alertItems = alerts.data?.content ?? alerts.data?.items ?? [];
  const alertTotalPages = alerts.data?.totalPages ?? 0;
  const activeList = watchlists.find((w) => w.id === activeListId);

  const handleRemoveWatchlistItem = async (id) => {
    try {
      await removeWatchlistItem.mutateAsync(id);
      toast.success('Listeden çıkarıldı');
    } catch (err) {
      toast.error(extractApiError(err, 'Silme başarısız'));
    }
  };

  const handleDeleteAlert = async (id) => {
    try {
      await deletePriceAlert.mutateAsync(id);
      toast.success('Alarm silindi');
    } catch (err) {
      toast.error(extractApiError(err, 'Silme başarısız'));
    }
  };

  const handleReactivateAlert = async (id) => {
    try {
      await reactivatePriceAlert.mutateAsync(id);
      toast.success('Alarm yeniden aktifleştirildi');
    } catch (err) {
      toast.error(extractApiError(err, 'Aktifleştirme başarısız'));
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
      toast.success('Liste silindi');
      if (activeListId === list.id) setActiveListId(null);
    } catch (err) {
      toast.error(extractApiError(err, 'Silme başarısız'));
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        icon={<Eye className="h-5 w-5" />}
        title="Takip"
        onRefresh={() => {
          lists.refetch();
          items.refetch();
          alerts.refetch();
        }}
        loading={lists.isFetching || items.isFetching || alerts.isFetching}
      />

      <section className="rounded-xl border border-border-default bg-bg-elevated card-hover overflow-hidden">
        <header className="flex items-center justify-between px-4 py-3 border-b border-border-default gap-3">
          <div className="flex items-center gap-2 shrink-0">
            <Star className="h-4 w-4 text-warning" />
            <h2 className="text-sm font-bold text-fg tracking-tight">Takip listelerim</h2>
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
            Asset ekle
          </motion.button>
        </header>
        <div className="px-4 py-3 border-b border-border-default">
          {lists.isLoading ? (
            <div className="flex items-center gap-2 text-xs text-fg-muted">
              <Loader2 className="h-3.5 w-3.5 animate-spin text-accent" />
              Listeler yükleniyor…
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
            value={sortBy === 'CUSTOM' ? '' : sortBy}
            direction={sortDirection.toLowerCase()}
            options={WATCHLIST_SORT_OPTIONS}
            onSortChange={(id) => {
              watchParams.setSort(id || '');
            }}
            onDirectionChange={(d) => watchParams.setDirection(d)}
            showDefault={false}
          />
        </div>
        <div className="grid grid-cols-[auto_auto_1fr_auto_auto] gap-3 px-4 py-2 border-b border-border-default text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
          <span className="w-6" />
          <span className="w-9">&nbsp;</span>
          <span>Varlık</span>
          <span className="text-right">Son fiyat</span>
          <span className="w-8" />
        </div>
        <div className="divide-y divide-border-default">
          {items.isLoading || activeListId == null ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted">
              <Loader2 className="h-4 w-4 animate-spin text-accent" />
              Yükleniyor…
            </div>
          ) : watchItems.length === 0 ? (
            <EmptyState
              icon={<Inbox className="h-5 w-5 text-fg-subtle" />}
              title={activeList ? `"${activeList.name}" listesi boş` : 'Liste boş'}
              hint="Bir crypto, hisse, döviz veya fon ekle, hareketleri buradan izle."
            />
          ) : (
            <DndContext
              sensors={dndSensors}
              collisionDetection={closestCenter}
              onDragEnd={(e) => {
                const { active, over } = e;
                if (!over || active.id === over.id || sortBy !== 'CUSTOM') return;
                const oldIndex = watchItems.findIndex((it) => it.id === active.id);
                const newIndex = watchItems.findIndex((it) => it.id === over.id);
                if (oldIndex < 0 || newIndex < 0) return;
                const reordered = arrayMove(watchItems, oldIndex, newIndex);
                reorderWatchlistItems.mutate(reordered.map((it) => it.id));
              }}
            >
              <SortableContext items={watchItems.map((it) => it.id)} strategy={verticalListSortingStrategy}>
                {watchItems.map((item) => (
                  <WatchlistRow key={item.id} item={item} onRemove={handleRemoveWatchlistItem} draggable={sortBy === 'CUSTOM'} />
                ))}
              </SortableContext>
            </DndContext>
          )}
        </div>
      </section>

      <section className="rounded-xl border border-border-default bg-bg-elevated card-hover overflow-hidden">
        <header className="flex items-center justify-between px-4 py-3 border-b border-border-default">
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-accent" />
            <h2 className="text-sm font-bold text-fg tracking-tight">Fiyat alarmları</h2>
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
            Alarm oluştur
          </motion.button>
        </header>
        <div className="grid grid-cols-[auto_1fr_auto_auto_auto_auto] gap-4 px-4 py-2 border-b border-border-default text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
          <span className="w-9">&nbsp;</span>
          <span>Varlık</span>
          <span>Yön</span>
          <span className="text-right min-w-[90px]">Eşik</span>
          <span>Durum</span>
          <span className="w-8" />
        </div>
        <div className="divide-y divide-border-default">
          {alerts.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted">
              <Loader2 className="h-4 w-4 animate-spin text-accent" />
              Yükleniyor…
            </div>
          ) : alertItems.length === 0 ? (
            <EmptyState
              icon={<Inbox className="h-5 w-5 text-fg-subtle" />}
              title="Henüz alarmın yok"
              hint="Bir asset için ABOVE/BELOW veya yüzde değişim alarmı kur."
            />
          ) : (
            alertItems.map((alert) => (
              <AlertRow key={alert.id} alert={alert} onDelete={handleDeleteAlert} onReactivate={handleReactivateAlert} />
            ))
          )}
        </div>
        <Pagination page={alertParams.page} totalPages={alertTotalPages} onPageChange={alertParams.setPage} />
      </section>

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
        title="Listeyi sil?"
        message={pendingDeleteList ? `"${pendingDeleteList.name}" ve içindeki tüm assetler kalıcı olarak silinecek.` : ''}
        confirmLabel="Sil"
        cancelLabel="Vazgeç"
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
