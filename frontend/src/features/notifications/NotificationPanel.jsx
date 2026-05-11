import { AnimatePresence } from 'framer-motion';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import {
  X, BellOff, Inbox, Check, CheckCheck, Trash2, AlertCircle, Zap, FileText,
  MessageSquare, Bell, Megaphone, Search, Newspaper, Briefcase, Sunrise, Sunset, RefreshCw,
} from 'lucide-react';
import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
  useDeleteNotification,
  useDeleteAllNotifications,
} from '../../shared/hooks/useNotifications';
import { useEffect, useState } from 'react';
import ConfirmDialog from '../../shared/components/modal/ConfirmDialog';
import BroadcastModal from '../admin/broadcast/BroadcastModal';
import { useAuth } from '../auth/AuthContext';
import { currentLocaleTag } from '../../shared/utils/formatters';

const TYPE_META = {
  PRICE_ALERT_FIRED: { Icon: AlertCircle, labelKey: 'notificationPanel.types.PRICE_ALERT_FIRED', tint: 'text-accent' },
  WATCHLIST_DELTA: { Icon: Zap, labelKey: 'notificationPanel.types.WATCHLIST_DELTA', tint: 'text-warning' },
  REPORT_READY: { Icon: FileText, labelKey: 'notificationPanel.types.REPORT_READY', tint: 'text-success' },
  MESSAGE: { Icon: MessageSquare, labelKey: 'notificationPanel.types.MESSAGE', tint: 'text-accent-secondary' },
  SYSTEM: { Icon: Bell, labelKey: 'notificationPanel.types.SYSTEM', tint: 'text-fg-muted' },
  MARKET_OPENED: { Icon: Sunrise, labelKey: 'notificationPanel.types.MARKET_OPENED', tint: 'text-success' },
  MARKET_CLOSED: { Icon: Sunset, labelKey: 'notificationPanel.types.MARKET_CLOSED', tint: 'text-fg-muted' },
  MARKET_DATA_UPDATED: { Icon: RefreshCw, labelKey: 'notificationPanel.types.MARKET_DATA_UPDATED', tint: 'text-accent' },
  NEWS_PUBLISHED: { Icon: Newspaper, labelKey: 'notificationPanel.types.NEWS_PUBLISHED', tint: 'text-accent-secondary' },
  PORTFOLIO_UPDATED: { Icon: Briefcase, labelKey: 'notificationPanel.types.PORTFOLIO_UPDATED', tint: 'text-success' },
};

function useRelativeTime() {
  const { t } = useTranslation();
  return (iso) => {
    if (!iso) return '';
    const diffMs = Date.now() - new Date(iso).getTime();
    const sec = Math.round(diffMs / 1000);
    if (sec < 60) return t('notificationPanel.justNow');
    const min = Math.round(sec / 60);
    if (min < 60) return t('notificationPanel.minutesAgo', { count: min });
    const hr = Math.round(min / 60);
    if (hr < 24) return t('notificationPanel.hoursAgo', { count: hr });
    const day = Math.round(hr / 24);
    if (day < 7) return t('notificationPanel.daysAgo', { count: day });
    return new Date(iso).toLocaleDateString(currentLocaleTag());
  };
}

function NotificationRow({ item, onRead, onDelete }) {
  const { t } = useTranslation();
  const relativeTime = useRelativeTime();
  const meta = TYPE_META[item.type] ?? TYPE_META.SYSTEM;
  const { Icon, labelKey, tint } = meta;
  const label = t(labelKey);
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
            {t('notificationPanel.markRead')}
          </button>
        )}
        <button
          onClick={() => onDelete(item.id)}
          className="flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-medium text-fg-muted hover:text-danger hover:bg-danger/5 transition-colors bg-transparent border-none cursor-pointer"
        >
          <Trash2 className="h-3 w-3" />
          {t('notificationPanel.deleteOne')}
        </button>
      </div>
    </motion.div>
  );
}

export default function NotificationPanel({ isOpen, onClose }) {
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');

  useEffect(() => {
    const id = setTimeout(() => setSearch(searchInput.trim()), 300);
    return () => clearTimeout(id);
  }, [searchInput]);

  const { data, isLoading } = useNotifications({ unreadOnly, page: 0, size: 50, search });
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();
  const deleteNotification = useDeleteNotification();
  const deleteAllNotifications = useDeleteAllNotifications();
  const [confirmClearOpen, setConfirmClearOpen] = useState(false);
  const [broadcastOpen, setBroadcastOpen] = useState(false);
  const isAdmin = hasRole('ADMIN');
  const isFiltering = search.length > 0;

  const items = data?.content ?? data?.items ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <>
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 z-[55] bg-black/40"
            onClick={onClose}
          />
          <motion.aside
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ duration: 0.32, ease: [0.32, 0.72, 0, 1] }}
            className="fixed top-0 right-0 bottom-0 z-[60] w-full sm:w-[420px] flex flex-col border-l border-border-default bg-bg-deep"
          >
            <header className="flex items-center justify-between px-5 h-14 border-b border-border-default shrink-0">
              <div className="flex items-center gap-2">
                <Bell className="h-4 w-4 text-accent" />
                <h2 className="text-sm font-bold text-fg tracking-tight font-display">{t('notificationPanel.title')}</h2>
                <span className="text-[10px] font-mono text-fg-subtle px-1.5 py-0.5 rounded-md bg-surface">
                  {total}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                {isAdmin && (
                  <motion.button
                    onClick={() => setBroadcastOpen(true)}
                    whileTap={{ scale: 0.94 }}
                    whileHover={{ y: -1 }}
                    transition={{ type: 'spring', stiffness: 400, damping: 25 }}
                    className="relative flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[11px] font-bold text-white overflow-hidden border-none cursor-pointer shadow-sm shadow-accent/25"
                    title={t('notificationPanel.broadcastTitle')}
                  >
                    <span aria-hidden className="absolute inset-0 bg-gradient-to-br from-accent via-accent-bright to-accent" />
                    <Megaphone className="relative h-3 w-3" />
                    <span className="relative">{t('notificationPanel.broadcastLabel')}</span>
                  </motion.button>
                )}
                <button
                  onClick={onClose}
                  className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </header>

            <div className="px-5 py-2.5 border-b border-border-default shrink-0">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-subtle pointer-events-none" />
                <input
                  type="text"
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  placeholder={t('notificationPanel.searchPlaceholder')}
                  className="w-full rounded-xl border border-border-default bg-bg-base/60 pl-8 pr-8 py-2 text-[12px] text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] focus:bg-bg-base transition-all"
                />
                {searchInput && (
                  <button
                    type="button"
                    onClick={() => setSearchInput('')}
                    className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center justify-center w-5 h-5 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
                  >
                    <X className="h-3 w-3" />
                  </button>
                )}
              </div>
            </div>

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
                  <span className="relative z-10">{t('notificationPanel.filterAll')}</span>
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
                  <span className="relative z-10">{t('notificationPanel.filterUnread')}</span>
                </button>
              </div>
              <div className="flex items-center gap-1.5">
                <button
                  onClick={() => markAllRead.mutate()}
                  disabled={markAllRead.isPending || total === 0}
                  className="flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border border-border-default cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <CheckCheck className="h-3 w-3" />
                  {t('notificationPanel.markRead')}
                </button>
                <button
                  onClick={() => setConfirmClearOpen(true)}
                  disabled={deleteAllNotifications.isPending || total === 0}
                  className="flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium text-fg-muted hover:text-danger hover:bg-danger/5 transition-colors bg-transparent border border-border-default hover:border-danger/30 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <Trash2 className="h-3 w-3" />
                  {t('notificationPanel.deleteAll')}
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
                    {unreadOnly ? t('notificationPanel.emptyUnread') : t('notificationPanel.empty')}
                  </p>
                  <p className="text-[11px] text-fg-subtle mt-1">
                    {unreadOnly ? t('notificationPanel.emptyUnreadHint') : t('notificationPanel.emptyHint')}
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
    </AnimatePresence>
    <BroadcastModal open={broadcastOpen} onClose={() => setBroadcastOpen(false)} />
    <ConfirmDialog
      open={confirmClearOpen}
      title={t('notificationPanel.confirmClearTitle')}
      message={t('notificationPanel.confirmClearMessage', { count: total })}
      confirmLabel={t('notificationPanel.deleteAll')}
      cancelLabel={t('common.cancel')}
      variant="danger"
      loading={deleteAllNotifications.isPending}
      onConfirm={() => {
        deleteAllNotifications.mutate(undefined, {
          onSettled: () => setConfirmClearOpen(false),
        });
      }}
      onCancel={() => setConfirmClearOpen(false)}
    />
    </>
  );
}
