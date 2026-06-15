import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Landmark, Plus } from 'lucide-react';
import { containerVariants } from '../../../shared/utils/animations';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import Spinner from '../../../shared/components/feedback/Spinner';
import Button from '../../../shared/components/buttons/Button';
import Pagination from '../../../shared/components/form/Pagination';
import { useBonds } from '../hooks/useFixedIncomePositions';
import { useClientListControls } from '../hooks/useClientListControls';
import FixedIncomeListControls from './FixedIncomeListControls';
import BondRow from './BondRow';
import BondFormModal from './BondFormModal';

const SORTERS = {
  maturity: (a, b) => new Date(a.maturityEnd || 0) - new Date(b.maturityEnd || 0),
  pnl: (a, b) => (Number(a.pnlTry) || 0) - (Number(b.pnlTry) || 0),
  value: (a, b) => (Number(a.currentValueTry) || 0) - (Number(b.currentValueTry) || 0),
  entry: (a, b) => new Date(a.entryDate || 0) - new Date(b.entryDate || 0),
};

export default function BondsList({ portfolioId }) {
  const { t } = useTranslation();
  const { data: bonds = [], isLoading } = useBonds(portfolioId);
  const [addOpen, setAddOpen] = useState(false);

  const controls = useClientListControls(bonds, {
    searchOf: (b) => `${b.bondSeriesCode || ''} ${b.bondName || ''} ${b.bondIsin || ''}`,
    statusOf: (b) => (b.exitDate ? 'closed' : 'open'),
    pnlOf: (b) => b.pnlTry,
    sorters: SORTERS,
    defaultSort: 'maturity',
    urlKey: 'b',
  });

  const sortOptions = useMemo(() => [
    { id: 'maturity', label: t('portfolio.fixedIncome.controls.sortMaturity') },
    { id: 'pnl', label: t('portfolio.fixedIncome.controls.sortPnl') },
    { id: 'value', label: t('portfolio.fixedIncome.controls.sortValue') },
    { id: 'entry', label: t('portfolio.fixedIncome.controls.sortEntry') },
  ], [t]);

  if (!portfolioId) return null;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 min-w-0">
          <Landmark className="h-4 w-4 text-accent shrink-0" />
          <h3 className="text-sm font-semibold text-fg truncate">{t('portfolio.bonds.title', { defaultValue: 'Tahviller' })}</h3>
          {controls.total > 0 && <span className="text-[11px] font-mono text-fg-muted">{controls.total}</span>}
        </div>
        <Button size="sm" leftIcon={<Plus className="h-3.5 w-3.5" />} onClick={() => setAddOpen(true)}>
          {t('portfolio.bonds.addAction')}
        </Button>
      </div>

      {!isLoading && bonds.length > 0 && (
        <FixedIncomeListControls
          controls={controls}
          sortOptions={sortOptions}
          searchPlaceholder={t('portfolio.fixedIncome.controls.searchBonds')}
          closedLabel={t('portfolio.bonds.statusSold')}
          idPrefix="bond"
        />
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Spinner size="md" tone="accent" />
        </div>
      ) : bonds.length === 0 ? (
        <EmptyState
          icon={<Landmark className="h-6 w-6 text-accent" />}
          message={t('portfolio.bonds.empty')}
          hint={t('portfolio.bonds.emptyHint')}
          action={{ label: t('portfolio.bonds.addAction'), icon: Plus, onClick: () => setAddOpen(true) }}
        />
      ) : controls.pageItems.length === 0 ? (
        <EmptyState
          icon={<Landmark className="h-6 w-6 text-fg-muted" />}
          message={t('portfolio.fixedIncome.controls.noMatch')}
        />
      ) : (
        <div className="space-y-3">
          {/* No AnimatePresence/popLayout here: its FLIP layout animations thrash when the filtered set swaps on an
              Open↔Sold toggle. A plain staggered container reveals rows cleanly and swaps without the jump. */}
          <motion.div
            key={`${controls.status}-${controls.pnl}-${controls.sort}-${controls.page}`}
            variants={containerVariants(0.05)}
            initial="hidden"
            animate="show"
            className="space-y-3"
          >
            {controls.pageItems.map((bond) => (
              <BondRow key={bond.id} portfolioId={portfolioId} bond={bond} />
            ))}
          </motion.div>
          <Pagination page={controls.page} totalPages={controls.totalPages} onPageChange={controls.setPage} />
        </div>
      )}

      {addOpen && (
        <BondFormModal
          mode="add"
          portfolioId={portfolioId}
          onClose={() => setAddOpen(false)}
        />
      )}
    </div>
  );
}
