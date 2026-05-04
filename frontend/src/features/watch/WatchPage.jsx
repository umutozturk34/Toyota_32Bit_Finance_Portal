import { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Eye, AlertCircle, Plus, Trash2, ArrowUp, ArrowDown, TrendingUp, TrendingDown,
  Loader2, Inbox, Star, ListPlus,
} from 'lucide-react';
import PageHeader from '../../shared/components/PageHeader';
import {
  useWatchlists,
  useWatchlistItems,
  useDeleteWatchlist,
  useRemoveWatchlistItem,
} from '../../shared/hooks/useWatchlist';
import { usePriceAlerts, useDeletePriceAlert } from '../../shared/hooks/usePriceAlerts';
import AddPriceAlertModal from './AddPriceAlertModal';
import AddWatchlistItemModal from './AddWatchlistItemModal';
import CreateWatchlistModal from './CreateWatchlistModal';
import { toast } from '../../shared/components/Toast';

const MARKET_LABELS = {
  CRYPTO: 'Crypto', STOCK: 'Stock', FOREX: 'Forex',
  FUND: 'Fund', COMMODITY: 'Emtia', BOND: 'Bond', NEWS: 'News',
};

const DIRECTION_META = {
  ABOVE: { label: 'üstüne', Icon: ArrowUp, tint: 'text-success' },
  BELOW: { label: 'altına', Icon: ArrowDown, tint: 'text-danger' },
  CHANGE_PCT_UP: { label: '% yükseliş', Icon: TrendingUp, tint: 'text-success' },
  CHANGE_PCT_DOWN: { label: '% düşüş', Icon: TrendingDown, tint: 'text-danger' },
};

function formatNumber(value) {
  if (value == null) return '—';
  const num = Number(value);
  if (Number.isNaN(num)) return '—';
  return num.toLocaleString('tr-TR', { maximumFractionDigits: 4 });
}

function relativeTime(iso) {
  if (!iso) return '—';
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.round(diffMs / 60000);
  if (min < 1) return 'az önce';
  if (min < 60) return `${min} dk önce`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr} sa önce`;
  const day = Math.round(hr / 24);
  return `${day} gün önce`;
}

function MarketBadge({ marketType }) {
  return (
    <span className="inline-flex items-center px-1.5 py-0.5 rounded-md text-[9px] font-mono uppercase tracking-wider bg-surface text-fg-muted">
      {MARKET_LABELS[marketType] ?? marketType}
    </span>
  );
}

function WatchlistTabs({ lists, activeId, onSelect, onCreate, onDelete }) {
  return (
    <div className="flex items-center gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: 'thin' }}>
      {lists.map((list) => {
        const active = list.id === activeId;
        return (
          <button
            key={list.id}
            onClick={() => onSelect(list.id)}
            className={`group relative inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors shrink-0 cursor-pointer ${
              active
                ? 'border-accent/50 bg-accent/10 text-accent'
                : 'border-border-default bg-bg-elevated text-fg-muted hover:border-border-hover hover:text-fg'
            }`}
          >
            {list.isDefault && <Star className="h-3 w-3 text-warning fill-warning" />}
            <span>{list.name}</span>
            <span className={`text-[10px] font-mono ${active ? 'text-accent/60' : 'text-fg-subtle'}`}>
              {list.itemCount}
            </span>
            {!list.isDefault && (
              <span
                role="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(list);
                }}
                className="ml-1 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center w-4 h-4 rounded text-fg-muted hover:text-danger cursor-pointer"
                title="Listeyi sil"
              >
                <Trash2 className="h-3 w-3" />
              </span>
            )}
          </button>
        );
      })}
      <button
        onClick={onCreate}
        className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-dashed border-border-default text-fg-muted hover:border-accent/50 hover:text-accent transition-colors shrink-0 cursor-pointer bg-transparent"
      >
        <ListPlus className="h-3.5 w-3.5" />
        Yeni liste
      </button>
    </div>
  );
}

function WatchlistRow({ item, onRemove }) {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: 80 }}
      transition={{ duration: 0.18 }}
      className="grid grid-cols-[auto_1fr_auto_auto] gap-3 items-center px-4 py-3 hover:bg-surface/50 transition-colors group"
    >
      <MarketBadge marketType={item.marketType} />
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-mono font-semibold text-fg truncate">{item.assetCode}</span>
          {item.deltaThreshold != null && (
            <span className="text-[10px] font-mono text-accent">±{item.deltaThreshold}%</span>
          )}
        </div>
        {item.note && <p className="text-[11px] text-fg-muted truncate">{item.note}</p>}
      </div>
      <div className="text-right">
        <div className="text-xs font-mono text-fg">{formatNumber(item.lastSeenPrice)}</div>
        <div className="text-[10px] text-fg-subtle">{relativeTime(item.lastSeenAt)}</div>
      </div>
      <button
        onClick={() => onRemove(item.id)}
        className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-danger hover:bg-danger/5 bg-transparent border-none cursor-pointer"
        title="Listeden çıkar"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </motion.div>
  );
}

function AlertRow({ alert, onDelete }) {
  const meta = DIRECTION_META[alert.direction] ?? DIRECTION_META.ABOVE;
  const { Icon, tint } = meta;
  const isFired = !alert.active && alert.triggeredAt;
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: 80 }}
      transition={{ duration: 0.18 }}
      className={`grid grid-cols-[auto_1fr_auto_auto_auto_auto] gap-3 items-center px-4 py-3 hover:bg-surface/50 transition-colors group ${
        isFired ? 'opacity-60' : ''
      }`}
    >
      <MarketBadge marketType={alert.marketType} />
      <span className="text-sm font-mono font-semibold text-fg truncate">{alert.assetCode}</span>
      <div className={`flex items-center gap-1 text-[11px] font-medium ${tint}`}>
        <Icon className="h-3 w-3" />
        <span>{meta.label}</span>
      </div>
      <span className="text-xs font-mono text-fg">{formatNumber(alert.threshold)}</span>
      <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${
        isFired ? 'bg-fg-subtle/10 text-fg-subtle' : 'bg-success/10 text-success'
      }`}>
        {isFired ? 'tetiklendi' : 'aktif'}
      </span>
      <button
        onClick={() => onDelete(alert.id)}
        className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-danger hover:bg-danger/5 bg-transparent border-none cursor-pointer"
        title="Sil"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </motion.div>
  );
}

export default function WatchPage() {
  const [addItemOpen, setAddItemOpen] = useState(false);
  const [createListOpen, setCreateListOpen] = useState(false);
  const [alertOpen, setAlertOpen] = useState(false);
  const [activeListId, setActiveListId] = useState(null);

  const lists = useWatchlists();
  const watchlists = useMemo(() => lists.data ?? [], [lists.data]);

  useEffect(() => {
    if (activeListId == null && watchlists.length > 0) {
      const def = watchlists.find((w) => w.isDefault) ?? watchlists[0];
      setActiveListId(def.id);
    }
  }, [activeListId, watchlists]);

  const items = useWatchlistItems(activeListId);
  const alerts = usePriceAlerts({ page: 0, size: 100 });
  const removeWatchlistItem = useRemoveWatchlistItem();
  const deletePriceAlert = useDeletePriceAlert();
  const deleteWatchlist = useDeleteWatchlist();

  const watchItems = items.data ?? [];
  const alertItems = alerts.data?.items ?? [];
  const activeList = watchlists.find((w) => w.id === activeListId);

  const handleRemoveWatchlistItem = async (id) => {
    try {
      await removeWatchlistItem.mutateAsync(id);
      toast.success('Listeden çıkarıldı');
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'Silme başarısız');
    }
  };

  const handleDeleteAlert = async (id) => {
    try {
      await deletePriceAlert.mutateAsync(id);
      toast.success('Alarm silindi');
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'Silme başarısız');
    }
  };

  const handleDeleteList = async (list) => {
    if (list.isDefault) return;
    if (!window.confirm(`"${list.name}" listesini silmek istediğine emin misin?`)) return;
    try {
      await deleteWatchlist.mutateAsync(list.id);
      toast.success('Liste silindi');
      if (activeListId === list.id) setActiveListId(null);
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'Silme başarısız');
    }
  };

  return (
    <div className="space-y-6">
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

      <section className="rounded-xl border border-border-default bg-bg-elevated overflow-hidden">
        <header className="flex items-center justify-between px-4 py-3 border-b border-border-default gap-3">
          <div className="flex items-center gap-2 shrink-0">
            <Star className="h-4 w-4 text-warning" />
            <h2 className="text-sm font-bold text-fg tracking-tight">Takip listelerim</h2>
          </div>
          <button
            onClick={() => setAddItemOpen(true)}
            disabled={activeListId == null}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-colors border-none cursor-pointer disabled:opacity-50"
          >
            <Plus className="h-3.5 w-3.5" />
            Asset ekle
          </button>
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
              onDelete={handleDeleteList}
            />
          )}
        </div>
        <div className="grid grid-cols-[auto_1fr_auto_auto] gap-3 px-4 py-2 border-b border-border-default text-[10px] font-mono uppercase tracking-wider text-fg-subtle">
          <span>Pazar</span>
          <span>Asset</span>
          <span className="text-right">Son fiyat</span>
          <span />
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
              hint="Bir crypto, stock veya forex ekle, hareketleri buradan izle."
            />
          ) : (
            <AnimatePresence initial={false}>
              {watchItems.map((item) => (
                <WatchlistRow key={item.id} item={item} onRemove={handleRemoveWatchlistItem} />
              ))}
            </AnimatePresence>
          )}
        </div>
      </section>

      <section className="rounded-xl border border-border-default bg-bg-elevated overflow-hidden">
        <header className="flex items-center justify-between px-4 py-3 border-b border-border-default">
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-accent" />
            <h2 className="text-sm font-bold text-fg tracking-tight">Fiyat alarmları</h2>
            <span className="text-[10px] font-mono text-fg-subtle px-1.5 py-0.5 rounded-md bg-surface">
              {alertItems.length}
            </span>
          </div>
          <button
            onClick={() => setAlertOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-colors border-none cursor-pointer"
          >
            <Plus className="h-3.5 w-3.5" />
            Alarm oluştur
          </button>
        </header>
        <div className="grid grid-cols-[auto_1fr_auto_auto_auto_auto] gap-3 px-4 py-2 border-b border-border-default text-[10px] font-mono uppercase tracking-wider text-fg-subtle">
          <span>Pazar</span>
          <span>Asset</span>
          <span>Yön</span>
          <span>Eşik</span>
          <span>Durum</span>
          <span />
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
            <AnimatePresence initial={false}>
              {alertItems.map((alert) => (
                <AlertRow key={alert.id} alert={alert} onDelete={handleDeleteAlert} />
              ))}
            </AnimatePresence>
          )}
        </div>
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
    </div>
  );
}

function EmptyState({ icon, title, hint }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-14 px-4 text-center">
      <div className="flex items-center justify-center w-10 h-10 rounded-2xl bg-surface mb-1">
        {icon}
      </div>
      <p className="text-sm font-medium text-fg-muted">{title}</p>
      <p className="text-[11px] text-fg-subtle max-w-xs">{hint}</p>
    </div>
  );
}
