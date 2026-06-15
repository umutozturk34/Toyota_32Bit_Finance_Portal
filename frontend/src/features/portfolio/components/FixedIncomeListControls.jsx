import { useTranslation } from 'react-i18next';
import SearchInput from '../../../shared/components/form/SearchInput';
import SortSelect from '../../../shared/components/form/SortSelect';
import FilterTabs from '../../../shared/components/form/FilterTabs';

/**
 * Shared filter/sort row for the bond + deposit lists: a search box, a status (All/Open/Closed) tab group, a P&L
 * (All/Profit/Loss) tab group and a sort selector — all driven by a {@link useClientListControls} instance, so the
 * two lists stay in lock-step. {@code idPrefix} keeps each list's animated tab indicators independent.
 */
export default function FixedIncomeListControls({ controls, sortOptions, searchPlaceholder, closedLabel, idPrefix }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="w-full sm:w-44">
        <SearchInput value={controls.search} onChange={controls.setSearch} placeholder={searchPlaceholder} />
      </div>
      <FilterTabs
        items={[
          { type: 'open', label: t('portfolio.positions.statusOpen') },
          { type: 'closed', label: closedLabel },
        ]}
        activeId={controls.status === 'all' ? 'ALL' : controls.status}
        onSelect={(id) => controls.setStatus(id === 'ALL' ? 'all' : id)}
        allLabel={t('portfolio.positions.statusAll')}
        showAll
        layoutId={`${idPrefix}-status`}
      />
      <FilterTabs
        items={[
          { type: 'profit', label: t('portfolio.fixedIncome.controls.profit') },
          { type: 'loss', label: t('portfolio.fixedIncome.controls.loss') },
        ]}
        activeId={controls.pnl === 'all' ? 'ALL' : controls.pnl}
        onSelect={(id) => controls.setPnl(id === 'ALL' ? 'all' : id)}
        allLabel={t('portfolio.positions.statusAll')}
        showAll
        layoutId={`${idPrefix}-pnl`}
      />
      <SortSelect
        value={controls.sort}
        direction={controls.direction}
        options={sortOptions}
        onSortChange={controls.setSort}
        onDirectionChange={controls.setDirection}
      />
    </div>
  );
}
