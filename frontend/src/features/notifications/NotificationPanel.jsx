import { motion, AnimatePresence } from 'framer-motion';
import {
  X, BellOff, Inbox, Check, CheckCheck, Trash2, AlertCircle, Zap, FileText,
  MessageSquare, Bell,
} from 'lucide-react';
import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
  useDeleteNotification,
  useDeleteAllNotifications,
} from '../../shared/hooks/useNotifications';
import { useState } from 'react';
import ConfirmDialog from '../../shared/components/ConfirmDialog';

const TYPE_META = {
  PRICE_ALERT_FIRED: { Icon: AlertCircle, label: 'Fiyat alarmı', tint: 'text-accent' },
  WATCHLIST_DELTA: { Icon: Zap, label: 'Takip listesi', tint: 'text-warning' },
  REPORT_READY: { Icon: FileText, label: 'Rapor', tint: 'text-success' },
  MESSAGE: { Icon: MessageSquare, label: 'Mesaj', tint: 'text-accent-secondary' },
  SYSTEM: { Icon: Bell, label: 'Sistem', tint: 'text-fg-muted' },
};

function relativeTime(iso) {
  if (!iso) return '';
  const diffMs = Date.now() - new Date(iso).getTime();
  const sec = Math.round(diffMs / 1000);
  if (sec < 60) return 'az önce';
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} dk önce`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr} sa önce`;
  const day = Math.round(hr / 24);
  if (day < 7) return `${day} gün önce`;
  return new Date(iso).toLocaleDateString('tr-TR');
}

function NotificationRow({ item, onRead, onDelete }) {
  const meta = TYPE_META[item.type] ?? TYPE_META.SYSTEM;
  const { Icon, label, tint } = meta;
  const isUnread = item.readAt == null;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: 80 }}
      transition={{ duration: 0.18 }}
      className={`group relative rounded-lg border px-3 py-3 transition-colors ${
        isUnread
          ? 'border-accent/30 bg-accent/5'
          : 'border-border-default bg-bg-elevated'
      }`}
    >
      <div className="flex items-start gap-3">
        <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-surface ${tint}`}>
          <Icon className="h-3.5 w-3.5" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 mb-0.5">
            <span className="text-[11px] font-semibold uppercase tracking-wide text-fg-muted">{label}</span>
            <span className="text-[10px] text-fg-subtle">·</span>
            <span className="text-[10px] text-fg-subtle">{relativeTime(item.createdAt)}</span>
            {isUnread && <span className="ml-auto w-1.5 h-1.5 rounded-full bg-accent animate-pulse-dot" />}
          </div>
          <h3 className="text-[13px] font-semibold text-fg truncate">{item.title}</h3>
          <p className="text-xs text-fg-muted leading-relaxed mt-0.5">{item.body}</p>
        </div>
      </div>

      <div className="mt-2 flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {isUnread && (
          <button
            onClick={() => onRead(item.id)}
            className="flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-medium text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
          >
            <Check className="h-3 w-3" />
            okundu
          </button>
        )}
        <button
          onClick={() => onDelete(item.id)}
          className="flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-medium text-fg-muted hover:text-danger hover:bg-danger/5 transition-colors bg-transparent border-none cursor-pointer"
        >
          <Trash2 className="h-3 w-3" />
          sil
        </button>
      </div>
    </motion.div>
  );
}

export default function NotificationPanel({ isOpen, onClose }) {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const { data, isLoading } = useNotifications({ unreadOnly, page: 0, size: 50 });
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();
  const deleteNotification = useDeleteNotification();
  const deleteAllNotifications = useDeleteAllNotifications();
  const [confirmClearOpen, setConfirmClearOpen] = useState(false);

  const items = data?.content ?? data?.items ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 z-[55] bg-black/40 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.aside
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', damping: 32, stiffness: 280 }}
            className="fixed top-0 right-0 bottom-0 z-[60] w-full sm:w-[420px] flex flex-col border-l border-border-default bg-bg-deep"
          >
            <header className="flex items-center justify-between px-5 h-14 border-b border-border-default shrink-0">
              <div className="flex items-center gap-2">
                <Bell className="h-4 w-4 text-accent" />
                <h2 className="text-sm font-bold text-fg tracking-tight font-display">Bildirimler</h2>
                <span className="text-[10px] font-mono text-fg-subtle px-1.5 py-0.5 rounded-md bg-surface">
                  {total}
                </span>
              </div>
              <button
                onClick={onClose}
                className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </header>

            <div className="flex items-center justify-between px-5 py-3 border-b border-border-default shrink-0">
              <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5">
                <button
                  onClick={() => setUnreadOnly(false)}
                  className={`relative px-3 py-1 rounded-md text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent ${
                    !unreadOnly ? 'text-accent' : 'text-fg-muted hover:text-fg'
                  }`}
                >
                  {!unreadOnly && (
                    <motion.span
                      layoutId="notif-filter"
                      className="absolute inset-0 rounded-md bg-accent/15"
                      transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                    />
                  )}
                  <span className="relative z-10">Tümü</span>
                </button>
                <button
                  onClick={() => setUnreadOnly(true)}
                  className={`relative px-3 py-1 rounded-md text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent ${
                    unreadOnly ? 'text-accent' : 'text-fg-muted hover:text-fg'
                  }`}
                >
                  {unreadOnly && (
                    <motion.span
                      layoutId="notif-filter"
                      className="absolute inset-0 rounded-md bg-accent/15"
                      transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                    />
                  )}
                  <span className="relative z-10">Okunmamış</span>
                </button>
              </div>
              <div className="flex items-center gap-1.5">
                <button
                  onClick={() => markAllRead.mutate()}
                  disabled={markAllRead.isPending || total === 0}
                  className="flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border border-border-default cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <CheckCheck className="h-3 w-3" />
                  okundu
                </button>
                <button
                  onClick={() => setConfirmClearOpen(true)}
                  disabled={deleteAllNotifications.isPending || total === 0}
                  className="flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium text-fg-muted hover:text-danger hover:bg-danger/5 transition-colors bg-transparent border border-border-default hover:border-danger/30 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <Trash2 className="h-3 w-3" />
                  tümünü sil
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto px-5 py-3 space-y-2" style={{ scrollbarWidth: 'thin' }}>
              {isLoading ? (
                <div className="flex items-center justify-center py-12">
                  <div className="h-5 w-5 rounded-full border-2 border-accent/30 border-t-accent animate-spin" />
                </div>
              ) : items.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
                  <div className="flex items-center justify-center w-12 h-12 rounded-2xl bg-surface mb-3">
                    {unreadOnly ? (
                      <BellOff className="h-5 w-5 text-fg-subtle" />
                    ) : (
                      <Inbox className="h-5 w-5 text-fg-subtle" />
                    )}
                  </div>
                  <p className="text-xs text-fg-muted">
                    {unreadOnly ? 'Okunmamış bildirim yok' : 'Henüz bildirim yok'}
                  </p>
                  <p className="text-[11px] text-fg-subtle mt-1">
                    {unreadOnly ? 'Hepsi okundu, tertemiz.' : 'Bildirimler buraya düşecek.'}
                  </p>
                </div>
              ) : (
                <AnimatePresence initial={false}>
                  {items.map((item) => (
                    <NotificationRow
                      key={item.id}
                      item={item}
                      onRead={(id) => markRead.mutate(id)}
                      onDelete={(id) => deleteNotification.mutate(id)}
                    />
                  ))}
                </AnimatePresence>
              )}
            </div>
          </motion.aside>
        </>
      )}
      <ConfirmDialog
        open={confirmClearOpen}
        title="Tüm bildirimleri sil?"
        message={`${total} bildirim kalıcı olarak silinecek. Bu işlem geri alınamaz.`}
        confirmLabel="Tümünü sil"
        cancelLabel="Vazgeç"
        variant="danger"
        loading={deleteAllNotifications.isPending}
        onConfirm={() => {
          deleteAllNotifications.mutate(undefined, {
            onSettled: () => setConfirmClearOpen(false),
          });
        }}
        onCancel={() => setConfirmClearOpen(false)}
      />
    </AnimatePresence>
  );
}
