import { AnimatePresence, motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import {
  BellOff, Inbox, CheckCheck, Trash2, AlertCircle, Zap,
  Bell, Megaphone, Search, Newspaper, Briefcase, Sunrise, Sunset, RefreshCw, TrendingUp, X,
} from 'lucide-react';
import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
  useDeleteNotification,
  useDeleteAllNotifications,
} from '../../shared/hooks/useNotifications';
import { useEffect, useMemo, useRef, useState } from 'react';
import ConfirmDialog from '../../shared/components/modal/ConfirmDialog';
import BroadcastModal from '../admin/broadcast/BroadcastModal';
import { useAuth } from '../auth/useAuth';
import { currentLocaleTag } from '../../shared/utils/formatters';
import SideDrawer from '../../shared/components/modal/SideDrawer';
import Button from '../../shared/components/buttons/Button';
import IconButton from '../../shared/components/buttons/IconButton';
import Spinner from '../../shared/components/feedback/Spinner';

const TYPE_META = {
  PRICE_ALERT_FIRED: { Icon: AlertCircle, labelKey: 'notificationPanel.types.PRICE_ALERT_FIRED', tint: 'text-accent' },
  WATCHLIST_DELTA: { Icon: Zap, labelKey: 'notificationPanel.types.WATCHLIST_DELTA', tint: 'text-warning' },
  SYSTEM: { Icon: Bell, labelKey: 'notificationPanel.types.SYSTEM', tint: 'text-fg-muted' },
  MARKET_OPENED: { Icon: Sunrise, labelKey: 'notificationPanel.types.MARKET_OPENED', tint: 'text-success' },
  MARKET_CLOSED: { Icon: Sunset, labelKey: 'notificationPanel.types.MARKET_CLOSED', tint: 'text-fg-muted' },
  MARKET_DATA_UPDATED: { Icon: RefreshCw, labelKey: 'notificationPanel.types.MARKET_DATA_UPDATED', tint: 'text-accent' },
  NEWS_PUBLISHED: { Icon: Newspaper, labelKey: 'notificationPanel.types.NEWS_PUBLISHED', tint: 'text-accent-secondary' },
  PORTFOLIO_UPDATED: { Icon: Briefcase, labelKey: 'notificationPanel.types.PORTFOLIO_UPDATED', tint: 'text-success' },
  MACRO_INDICATORS_UPDATED: { Icon: TrendingUp, labelKey: 'notificationPanel.types.MACRO_INDICATORS_UPDATED', tint: 'text-warning' },
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

const AUTO_READ_DELAY_MS = 1500;

function useAutoMarkRead(ref, isUnread, onRead) {
  useEffect(() => {
    const node = ref.current;
    if (!node || !isUnread) return;
    let root = node.parentElement;
    while (root && root !== document.body) {
      const style = window.getComputedStyle(root);
      const overflowY = style.overflowY;
      if (overflowY === 'auto' || overflowY === 'scroll') break;
      root = root.parentElement;
    }
    let timer = null;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          timer = setTimeout(() => onRead(), AUTO_READ_DELAY_MS);
        } else if (timer) {
          clearTimeout(timer);
          timer = null;
        }
      },
      { root: root && root !== document.body ? root : null, threshold: 0.6 },
    );
    observer.observe(node);
    return () => {
      if (timer) clearTimeout(timer);
      observer.disconnect();
    };
  }, [ref, isUnread, onRead]);
}

function NotificationRow({ item, onRead, onDelete }) {
  const { t } = useTranslation();
  const relativeTime = useRelativeTime();
  const meta = TYPE_META[item.type] ?? TYPE_META.SYSTEM;
  const { Icon, labelKey, tint } = meta;
  const label = t(labelKey);
  const isUnread = item.readAt == null;
  const rowRef = useRef(null);
  useAutoMarkRead(rowRef, isUnread, () => onRead(item.id));

  return (
    <motion.div
      ref={rowRef}
      layout
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: 80 }}
      transition={{ duration: 0.18 }}
      onClick={() => { if (isUnread) onRead(item.id); }}
      className={`group relative rounded-lg border px-3 py-3 transition-colors ${
        isUnread
          ? 'border-accent/30 bg-accent/5 cursor-pointer hover:bg-accent/8'
          : 'border-border-default bg-bg-elevated'
      }`}
    >
      <div className="flex items-start gap-2.5 sm:gap-3 min-w-0">
        <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-surface ${tint}`}>
          <Icon className="h-3.5 w-3.5" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5 sm:gap-2 mb-0.5 flex-wrap">
            <span className="text-[11px] font-semibold uppercase tracking-wide text-fg-muted truncate max-w-full">{label}</span>
            <span className="text-[10px] text-fg-subtle hidden sm:inline">·</span>
            <span className="text-[10px] text-fg-subtle whitespace-nowrap">{relativeTime(item.createdAt)}</span>
            {isUnread && <span className="ml-auto w-1.5 h-1.5 rounded-full bg-accent animate-pulse-dot shrink-0" />}
          </div>
          <h3 className="text-[13px] font-semibold text-fg break-words line-clamp-2">{item.title}</h3>
          <p className="text-xs text-fg-muted leading-relaxed mt-0.5 break-words">{item.body}</p>
        </div>
      </div>

      <div className="mt-2 flex items-center justify-end gap-1 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
        <Button
          variant="ghost"
          size="xs"
          leftIcon={<Trash2 className="h-3 w-3" />}
          onClick={(e) => { e.stopPropagation(); onDelete(item.id); }}
          className="hover:text-danger hover:bg-danger/5"
        >
          {t('notificationPanel.deleteOne')}
        </Button>
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

  const {
    data,
    isLoading,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
  } = useNotifications({ unreadOnly, size: 20, search });
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();
  const deleteNotification = useDeleteNotification();
  const deleteAllNotifications = useDeleteAllNotifications();
  const [confirmClearOpen, setConfirmClearOpen] = useState(false);
  const [broadcastOpen, setBroadcastOpen] = useState(false);
  const isAdmin = hasRole('ADMIN');

  const items = useMemo(() => (data?.pages ?? []).flatMap((p) => p?.content ?? []), [data]);
  const total = data?.pages?.[0]?.totalElements ?? 0;
  const sentinelRef = useRef(null);

  useEffect(() => {
    // The panel stays mounted and preloads the "all" list, so the sentinel only enters the DOM when
    // the drawer opens — depend on isOpen so the observer attaches then, not just on data/tab changes.
    const node = sentinelRef.current;
    if (!isOpen || !node || !hasNextPage || isFetchingNextPage) return;
    let root = node.parentElement;
    while (root && root !== document.body) {
      const style = window.getComputedStyle(root);
      const overflowY = style.overflowY;
      if (overflowY === 'auto' || overflowY === 'scroll') break;
      root = root.parentElement;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) fetchNextPage();
      },
      { root: root && root !== document.body ? root : null, rootMargin: '120px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage, items.length, isOpen]);

  const headerActions = (
    <>
      <span className="text-[10px] font-mono text-fg-subtle px-1.5 py-0.5 rounded-md bg-surface">{total}</span>
      {isAdmin && (
        <Button
          variant="gradient"
          size="xs"
          leftIcon={<Megaphone className="h-3 w-3" />}
          onClick={() => setBroadcastOpen(true)}
          title={t('notificationPanel.broadcastTitle')}
        >
          {t('notificationPanel.broadcastLabel')}
        </Button>
      )}
    </>
  );

  return (
    <>
      <SideDrawer
        open={isOpen}
        onClose={onClose}
        icon={Bell}
        iconTint="text-accent"
        title={t('notificationPanel.title')}
        headerActions={headerActions}
        closeLabel={t('notificationPanel.close', { defaultValue: 'close' })}
      >
        <div className="px-4 sm:px-5 py-2.5 border-b border-border-default shrink-0">
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
              <span className="absolute right-2 top-1/2 -translate-y-1/2">
                <IconButton
                  variant="ghost"
                  size={7}
                  shape="square"
                  icon={<X className="h-3 w-3" />}
                  aria-label={t('common.clear')}
                  onClick={() => setSearchInput('')}
                  className="w-5 h-5"
                />
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center justify-between gap-2 px-4 sm:px-5 py-3 border-b border-border-default shrink-0 flex-wrap">
          <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5">
            <Button
              variant="segment"
              size="xs"
              segmentActive={!unreadOnly}
              layoutId="notif-filter"
              motionPreset="none"
              onClick={() => setUnreadOnly(false)}
            >
              {t('notificationPanel.filterAll')}
            </Button>
            <Button
              variant="segment"
              size="xs"
              segmentActive={unreadOnly}
              layoutId="notif-filter"
              motionPreset="none"
              onClick={() => setUnreadOnly(true)}
            >
              {t('notificationPanel.filterUnread')}
            </Button>
          </div>
          <div className="flex items-center gap-1.5">
            <Button
              variant="secondary"
              size="xs"
              leftIcon={<CheckCheck className="h-3 w-3" />}
              onClick={() => markAllRead.mutate()}
              disabled={markAllRead.isPending || total === 0}
            >
              {t('notificationPanel.markRead')}
            </Button>
            <Button
              variant="secondary"
              size="xs"
              leftIcon={<Trash2 className="h-3 w-3" />}
              onClick={() => setConfirmClearOpen(true)}
              disabled={deleteAllNotifications.isPending || total === 0}
              className="hover:text-danger hover:bg-danger/5 hover:border-danger/30"
            >
              {t('notificationPanel.deleteAll')}
            </Button>
          </div>
        </div>

        <div className="px-4 sm:px-5 py-3 space-y-2">
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Spinner size="md" tone="accent" />
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
            <>
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
              {hasNextPage && (
                <div ref={sentinelRef} className="flex items-center justify-center py-4">
                  {isFetchingNextPage && <Spinner size="sm" tone="accent" />}
                </div>
              )}
            </>
          )}
        </div>
      </SideDrawer>

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
