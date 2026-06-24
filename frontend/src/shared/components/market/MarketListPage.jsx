import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import { useAuth } from '../../../features/auth/useAuth';
import { getChangeClass } from '../../utils/formatters';
import { containerVariants } from '../../utils/animations';
import { Skeleton } from '../feedback/Skeleton';
import ErrorState from '../feedback/ErrorState';
import EmptyState from '../feedback/EmptyState';
import PageHeader from '../layout/PageHeader';
import MarketStatusBadge from '../layout/MarketStatusBadge';
import SearchInput from '../form/SearchInput';
import SortSelect from '../form/SortSelect';
import Pagination from '../form/Pagination';
import MarketAddPositionModal from '../../../features/portfolio/components/MarketAddPositionModal';
import FilterTabs from '../form/FilterTabs';
import { toast } from '../feedback/toastBus';
import useMarketListData from '../../hooks/useMarketListData';

// A placeholder that mirrors a real asset card's structure and OUTER dimensions (the size="sm" AssetCard: rounded
// border + md padding), so the loading grid reads as the same cards materialising — not an oversized "showcase"
// behind the real ones. A page with a differently-shaped card can pass its own via renderCardSkeleton.
function DefaultCardSkeleton() {
  return (
    <div className="rounded-xl border border-border-default bg-bg-elevated/50 p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1 space-y-1.5">
          <Skeleton w="45%" h="0.85rem" />
          <Skeleton w="80%" h="0.6rem" />
        </div>
        <Skeleton w="2.75rem" h="1.1rem" className="rounded-md" />
      </div>
      <div className="mt-3 space-y-1.5">
        <Skeleton w="52%" h="1.3rem" className="rounded-md" />
        <Skeleton w="36%" h="0.75rem" />
      </div>
      <div className="mt-3 space-y-2 border-t border-border-default pt-3">
        {[0, 1, 2].map((j) => (
          <div key={j} className="flex items-center justify-between">
            <Skeleton w="30%" h="0.65rem" />
            <Skeleton w="22%" h="0.65rem" />
          </div>
        ))}
      </div>
      <div className="mt-3"><Skeleton w="58%" h="0.6rem" /></div>
    </div>
  );
}

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
  renderCardSkeleton,
  errorMessage,
  emptyMessage,
  emptyHint,
  gridClass = 'grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[400px] content-start',
  headerChildren,
  preGridChildren,
  filterShowAll = true,
  animatePresence = false,
  buyModalComponent: BuyModalComponent = MarketAddPositionModal,
  dataTour,
}) {
  const { t } = useTranslation();
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
      toast.success(t('marketList.updateStarted'), response.message || successMsg || t('marketList.updateStarted'));
      if (refetchDelay) setTimeout(refetch, refetchDelay);
    } catch (err) {
      // 429 (rate limit / pool busy) is already surfaced by the global api interceptor's rate-limit toast;
      // showing another here double-toasts the "system busy" message.
      if (err.response?.status !== 429) {
        toast.error(t('marketList.updateFailed'), err.response?.data?.message || err.message);
      }
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

  if (error && items.length === 0) return <ErrorState message={errorMessage} onRetry={refetch} />;

  const cardRenderer = (asset) => renderCard(asset, {
    cls: getChangeClass(asset.changePercent),
    setBuyTarget,
  });

  // Keyed on the full query identity so the grid re-staggers on every sort/filter/page/search change (not just
  // first mount) — a re-sort lands as one beat instead of hard-swapping the cards.
  const gridKey = JSON.stringify(effectiveQueryParams);
  const grid = (
    <motion.div key={gridKey} variants={containerVariants(0.06)} initial="hidden" animate="show" className={gridClass}>
      {items.map(cardRenderer)}
    </motion.div>
  );

  return (
    <div className="space-y-6 py-6" data-tour={dataTour}>
      <PageHeader
        icon={icon}
        title={
          <span className="inline-flex items-center gap-3 flex-wrap">
            {title}
            {marketType && <MarketStatusBadge market={marketType.toUpperCase()} compact />}
          </span>
        }
        onRefresh={refetch}
        loading={isLoading}
        isAdmin={isAdmin}
        adminActions={adminActions}
        updating={updating}
      />

      {headerChildren}

      <div className="flex flex-wrap items-center gap-3">
        <div className="w-full sm:w-48">
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
          <div className="w-full sm:w-auto sm:flex-1 min-w-0">
            <FilterTabs
              items={filterConfig.tabItems}
              activeId={filterConfig.activeId}
              onSelect={filterConfig.onSelect}
              showAll={filterShowAll}
              layoutId={filterConfig.layoutId}
            />
          </div>
        )}
      </div>

      {preGridChildren}

      {isLoading && items.length === 0 && (
        <div className={gridClass} aria-hidden="true">
          {Array.from({ length: 12 }).map((_, i) => (
            renderCardSkeleton ? <div key={i}>{renderCardSkeleton()}</div> : <DefaultCardSkeleton key={i} />
          ))}
        </div>
      )}

      {items.length > 0 && (animatePresence ? <AnimatePresence>{grid}</AnimatePresence> : grid)}

      {items.length === 0 && !isLoading && (
        <EmptyState
          icon={emptyIcon || icon}
          message={listParams.search ? t('marketList.noSearchMatch') : emptyMessage}
          hint={!listParams.search && isAdmin ? emptyHint : undefined}
        />
      )}

      <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />

      {buyTarget && (
        <BuyModalComponent
          assetType={marketType}
          assetCode={buyTarget.assetCode}
          assetName={buyTarget.assetName}
          assetImage={buyTarget.assetImage}
          currentPrice={buyTarget.price}
          metadata={buyTarget.metadata}
          onClose={() => setBuyTarget(null)}
          onComplete={() => setBuyTarget(null)}
        />
      )}
    </div>
  );
}
