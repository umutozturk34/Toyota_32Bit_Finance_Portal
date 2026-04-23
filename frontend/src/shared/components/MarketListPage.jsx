import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../../features/auth/AuthContext';
import { getChangeClass } from '../utils/formatters';
import { containerVariants } from '../utils/animations';
import LoadingState from './LoadingState';
import ErrorState from './ErrorState';
import EmptyState from './EmptyState';
import PageHeader from './PageHeader';
import SearchInput from './SearchInput';
import SortSelect from './SortSelect';
import Pagination from './Pagination';
import BuyModal from './BuyModal';
import FilterTabs from './FilterTabs';
import { toast } from './Toast';
import useMarketListData from '../hooks/useMarketListData';

export default function MarketListPage({
  title,
  icon,
  emptyIcon,
  marketType,
  service,
  queryKey,
  listParams,
  queryParams,
  searchPlaceholder,
  countLabel,
  sortOptions,
  filterConfig,
  adminTriggers,
  renderCard,
  loadingMessage,
  errorMessage,
  emptyMessage,
  emptyHint,
  gridClass = 'grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[400px] content-start',
  headerChildren,
  preGridChildren,
  filterShowAll = true,
  animatePresence = false,
}) {
  const { hasRole } = useAuth();
  const [buyTarget, setBuyTarget] = useState(null);
  const [updating, setUpdating] = useState({});
  const isAdmin = hasRole('ADMIN');

  const effectiveQueryParams = queryParams ?? listParams.params;

  const { data, isLoading, error, refetch } = useMarketListData(queryKey, service.getAll, effectiveQueryParams);

  const items = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;

  const handleTrigger = async (key, fn, successMsg, refetchDelay) => {
    setUpdating(prev => ({ ...prev, [key]: true }));
    try {
      const response = await fn();
      toast.success('Güncelleme Başlatıldı', response.message || successMsg || 'Güncelleme başlatıldı');
      if (refetchDelay) setTimeout(refetch, refetchDelay);
    } catch (err) {
      toast.error('Güncelleme Hatası', err.response?.data?.message || err.message);
    } finally {
      setUpdating(prev => ({ ...prev, [key]: false }));
    }
  };

  const adminActions = (adminTriggers || []).map(t => ({
    key: t.key,
    label: t.label,
    title: t.title,
    handler: () => handleTrigger(t.key, t.fn, t.successMsg, t.refetchDelay),
  }));

  if (isLoading && items.length === 0) return <LoadingState message={loadingMessage} />;
  if (error) return <ErrorState message={errorMessage} onRetry={refetch} />;

  const cardRenderer = (asset) => renderCard(asset, {
    cls: getChangeClass(asset.changePercent),
    setBuyTarget,
  });

  const grid = (
    <motion.div variants={containerVariants(0.06)} initial="hidden" animate="show" className={gridClass}>
      {items.map(cardRenderer)}
    </motion.div>
  );

  return (
    <div className="space-y-6 py-6">
      <PageHeader
        icon={icon}
        title={title}
        onRefresh={refetch}
        loading={isLoading}
        isAdmin={isAdmin}
        adminActions={adminActions}
        updating={updating}
      />

      {headerChildren}

      <div className="flex flex-wrap items-center gap-3">
        <div className="w-48">
          <SearchInput
            value={listParams.search}
            onChange={listParams.setSearch}
            placeholder={searchPlaceholder}
            withSuggestions
            filterType={marketType}
          />
        </div>
        {totalElements > 0 && countLabel && (
          <span className="text-xs text-fg-muted">{totalElements} {countLabel}</span>
        )}
        <SortSelect
          value={listParams.sort}
          direction={listParams.direction}
          options={sortOptions}
          onSortChange={listParams.setSort}
          onDirectionChange={listParams.setDirection}
        />
        {filterConfig && filterConfig.tabItems.length > 0 && (
          <FilterTabs
            items={filterConfig.tabItems}
            activeId={filterConfig.activeId}
            onSelect={filterConfig.onSelect}
            showAll={filterShowAll}
            layoutId={filterConfig.layoutId}
          />
        )}
      </div>

      {preGridChildren}

      {items.length > 0 && (animatePresence ? <AnimatePresence>{grid}</AnimatePresence> : grid)}

      {items.length === 0 && !isLoading && (
        <EmptyState
          icon={emptyIcon || icon}
          message={listParams.search ? 'Aramayla eşleşen sonuç bulunamadı.' : emptyMessage}
          hint={!listParams.search && isAdmin ? emptyHint : undefined}
        />
      )}

      <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />

      {buyTarget && (
        <BuyModal
          assetType={marketType}
          assetCode={buyTarget.assetCode}
          assetName={buyTarget.assetName}
          currentPrice={buyTarget.price}
          onClose={() => setBuyTarget(null)}
          onComplete={() => setBuyTarget(null)}
        />
      )}
    </div>
  );
}
