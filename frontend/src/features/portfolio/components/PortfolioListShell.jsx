import { containerVariants } from '../../../shared/utils/animations';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import SearchInput from '../../../shared/components/form/SearchInput';
import SortSelect from '../../../shared/components/form/SortSelect';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import Pagination from '../../../shared/components/form/Pagination';
import { ASSET_TYPE_FILTERS } from '../../../shared/constants/assetTypes';

export default function PortfolioListShell({
  listParams,
  totalPages,
  sortOptions,
  searchPlaceholder,
  filterLayoutId,
  isEmpty,
  emptyIcon,
  emptyMessage,
  emptyHint,
  children,
}) {
  const assetTypeFilter = listParams.filter || '';

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <div className="w-48">
          <SearchInput
            value={listParams.search}
            onChange={listParams.setSearch}
            placeholder={searchPlaceholder}
          />
        </div>
        <SortSelect
          value={listParams.sort}
          direction={listParams.direction}
          options={sortOptions}
          onSortChange={listParams.setSort}
          onDirectionChange={listParams.setDirection}
        />
        <FilterTabs
          items={ASSET_TYPE_FILTERS.filter(f => f.id).map(f => ({ type: f.id, label: f.label }))}
          activeId={assetTypeFilter || 'ALL'}
          onSelect={(id) => listParams.setFilter(id === 'ALL' ? '' : id)}
          showAll={true}
          layoutId={filterLayoutId}
        />
      </div>

      {isEmpty ? (
        <EmptyState icon={emptyIcon} message={emptyMessage} hint={emptyHint} />
      ) : (
        <motion.div
          variants={containerVariants(0.04)}
          initial="hidden"
          animate="show"
          className="space-y-2 min-h-[500px]"
        >
          {children}
        </motion.div>
      )}

      <Pagination
        page={listParams.page}
        totalPages={totalPages}
        onPageChange={listParams.setPage}
      />
    </div>
  );
}
