import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Users, Search, Ban, ShieldCheck, AlertCircle, Mail, ChevronLeft, ChevronRight, User } from 'lucide-react';
import PageHeader from '../../../shared/components/layout/PageHeader';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import AdminTabBar from './AdminTabBar';
import Spinner from '../../../shared/components/feedback/Spinner';
import { toast } from '../../../shared/components/feedback/toastBus';
import { useAuth } from '../../auth/useAuth';
import { useAdminUsers, useAdminUserCount, useBanUser, useUnbanUser } from '../hooks/useAdminUsers';

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

function formatDate(iso, localeTag) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: 'numeric' });
}

function StatusBadge({ enabled }) {
  const { t } = useTranslation();
  return (
    <span className={`inline-flex w-fit items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-semibold ${
      enabled ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'
    }`}>
      {enabled ? <ShieldCheck className="h-3 w-3" /> : <Ban className="h-3 w-3" />}
      {enabled ? t('adminUsers.statusActive') : t('adminUsers.statusBanned')}
    </span>
  );
}

export default function AdminUsersPage() {
  const { t } = useTranslation();
  const localeTag = t('common.localeTag');
  const { user: currentUser } = useAuth();
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [pageSize, setPageSize] = useState(25);
  const [page, setPage] = useState(0);
  const [pendingId, setPendingId] = useState(null);

  const first = page * pageSize;
  const { data: users, isLoading, isFetching, error, refetch } = useAdminUsers({ first, max: pageSize, search });
  const { data: total = 0, refetch: refetchCount } = useAdminUserCount(search);
  const banMutation = useBanUser();
  const unbanMutation = useUnbanUser();

  const handleRefresh = () => {
    refetch();
    refetchCount();
  };

  const totalPages = useMemo(() => Math.max(1, Math.ceil(total / pageSize)), [total, pageSize]);
  const canPrev = page > 0;
  const canNext = page < totalPages - 1;

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setSearch(searchInput.trim());
    setPage(0);
  };

  const handlePageSizeChange = (size) => {
    setPageSize(size);
    setPage(0);
  };

  const handleToggleBan = async (user) => {
    setPendingId(user.id);
    try {
      if (user.enabled) {
        await banMutation.mutateAsync(user.id);
        toast.success(t('adminUsers.toast.banned'), t('adminUsers.toast.bannedBody', { username: user.username }));
      } else {
        await unbanMutation.mutateAsync(user.id);
        toast.success(t('adminUsers.toast.unbanned'), t('adminUsers.toast.unbannedBody', { username: user.username }));
      }
    } catch (err) {
      toast.error(t('error.actionFailed'), err?.response?.data?.message || t('adminUsers.toast.unknownError'));
    } finally {
      setPendingId(null);
    }
  };

  if (error) return <ErrorState message={t('adminUsers.loadError')} onRetry={() => window.location.reload()} />;

  return (
    <div className="space-y-5">
      <PageHeader
        icon={<Users className="h-5 w-5" />}
        title={t('adminUsers.title')}
        onRefresh={handleRefresh}
        loading={isFetching}
      />

      <AdminTabBar />

      <form onSubmit={handleSearchSubmit} className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-fg-muted pointer-events-none" />
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            maxLength={64}
            placeholder={t('adminUsers.searchPlaceholder')}
            className="w-full pl-9 pr-3 py-2 rounded-lg border border-border-default bg-bg-elevated text-sm text-fg placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
        </div>
        <button
          type="submit"
          className="px-4 py-2 rounded-lg bg-accent hover:bg-accent-bright text-sm font-semibold text-white border-none cursor-pointer transition-colors"
        >
          {t('common.search')}
        </button>
      </form>

      <div className="rounded-xl border border-border-default bg-bg-elevated overflow-hidden">
        <div className="hidden md:grid grid-cols-[2fr_2fr_1fr_1fr_140px] gap-3 px-4 py-3 border-b border-border-default text-[11px] font-semibold text-fg-muted uppercase tracking-wide">
          <span>{t('adminUsers.col.user')}</span>
          <span>{t('adminUsers.col.email')}</span>
          <span>{t('adminUsers.col.signupDate')}</span>
          <span>{t('adminUsers.col.status')}</span>
          <span className="text-right">{t('adminUsers.col.action')}</span>
        </div>
        {isLoading && (
          <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted">
            <Spinner size="sm" tone="accent" />
            {t('common.loading')}
          </div>
        )}
        {!isLoading && (!users || users.length === 0) && (
          <div className="flex flex-col items-center justify-center gap-2 py-12 text-sm text-fg-muted">
            <AlertCircle className="h-5 w-5" />
            {t('adminUsers.noUsersFound')}
          </div>
        )}
        {!isLoading && users && users.length > 0 && users.map((user) => (
          <motion.div
            key={user.id}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            className="grid grid-cols-1 md:grid-cols-[2fr_2fr_1fr_1fr_140px] gap-2 md:gap-3 items-start md:items-center px-4 py-3 border-b border-border-default last:border-b-0 text-sm hover:bg-surface transition-colors"
          >
            <div className="min-w-0">
              <div className="font-medium text-fg truncate">{user.username}</div>
              {(user.firstName || user.lastName) && (
                <div className="text-[11px] text-fg-muted truncate">
                  {[user.firstName, user.lastName].filter(Boolean).join(' ')}
                </div>
              )}
            </div>
            <div className="flex items-center gap-1.5 text-fg-muted truncate min-w-0">
              <Mail className="h-3 w-3 shrink-0" />
              <span className="truncate">{user.email || '—'}</span>
            </div>
            <span className="text-[11px] text-fg-muted font-mono">{formatDate(user.createdAt, localeTag)}</span>
            <StatusBadge enabled={user.enabled} />
            {user.id === currentUser?.id ? (
              <span className="justify-self-end flex items-center gap-1.5 rounded-md px-2.5 py-1 text-[11px] font-semibold bg-accent/10 text-accent">
                <User className="h-3 w-3" /> {t('adminUsers.you')}
              </span>
            ) : (
              <button
                onClick={() => handleToggleBan(user)}
                disabled={pendingId === user.id}
                className={`justify-self-end flex items-center gap-1.5 rounded-md px-2.5 py-1 text-[11px] font-semibold border-none cursor-pointer transition-all disabled:opacity-50 ${
                  user.enabled
                    ? 'bg-danger/10 text-danger hover:bg-danger/20'
                    : 'bg-success/10 text-success hover:bg-success/20'
                }`}
              >
                {pendingId === user.id ? (
                  <Spinner size="xs" tone="inherit" />
                ) : user.enabled ? (
                  <><Ban className="h-3 w-3" /> {t('adminUsers.banAction')}</>
                ) : (
                  <><ShieldCheck className="h-3 w-3" /> {t('adminUsers.unbanAction')}</>
                )}
              </button>
            )}
          </motion.div>
        ))}
      </div>

      <div className="flex items-center justify-between gap-3 text-xs text-fg-muted flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          <span>{t('adminUsers.pageSize')}</span>
          <div className="flex gap-0.5 rounded-md border border-border-default bg-bg-elevated p-0.5">
            {PAGE_SIZE_OPTIONS.map((size) => (
              <button
                key={size}
                onClick={() => handlePageSizeChange(size)}
                className={`px-2 py-0.5 text-[11px] font-medium rounded transition-colors border-none cursor-pointer ${
                  pageSize === size ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg-muted hover:text-fg'
                }`}
              >
                {size}
              </button>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span>
            <span className="font-mono text-fg">{total === 0 ? 0 : first + 1}-{Math.min(first + pageSize, total)}</span>
            <span className="mx-1.5 text-fg-subtle">/</span>
            <span className="font-mono text-fg">{total}</span>
          </span>
          <div className="flex gap-1">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={!canPrev}
              className="flex items-center justify-center w-7 h-7 rounded-md border border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:bg-surface transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <span className="flex items-center px-2 font-mono text-fg">{page + 1}/{totalPages}</span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={!canNext}
              className="flex items-center justify-center w-7 h-7 rounded-md border border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:bg-surface transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>

    </div>
  );
}
