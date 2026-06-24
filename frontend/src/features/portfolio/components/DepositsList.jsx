import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Landmark, Plus } from 'lucide-react';
import { containerVariants } from '../../../shared/utils/animations';
import Button from '../../../shared/components/buttons/Button';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import Spinner from '../../../shared/components/feedback/Spinner';
import Pagination from '../../../shared/components/form/Pagination';
import DepositRow from './DepositRow';
import DepositFormModal from './DepositFormModal';
import { useDeposits } from '../hooks/useFixedIncomePositions';
import { useClientListControls } from '../hooks/useClientListControls';
import FixedIncomeListControls from './FixedIncomeListControls';

const SORTERS = {
  maturity: (a, b) => new Date(a.maturityDate || 0) - new Date(b.maturityDate || 0),
  pnl: (a, b) => (Number(a.pnlTry) || 0) - (Number(b.pnlTry) || 0),
  value: (a, b) => (Number(a.currentValueTry) || 0) - (Number(b.currentValueTry) || 0),
  start: (a, b) => new Date(a.startDate || 0) - new Date(b.startDate || 0),
};

export default function DepositsList({ portfolioId, portfolioPicker }) {
  const { t } = useTranslation();
  const { data, isLoading } = useDeposits(portfolioId);
  const deposits = useMemo(() => data || [], [data]);
  const [addOpen, setAddOpen] = useState(false);

  const controls = useClientListControls(deposits, {
    searchOf: (d) => `${d.currency || ''} ${d.indicatorCode || ''}`,
    statusOf: (d) => (d.active ? 'open' : 'closed'),
    pnlOf: (d) => d.pnlTry,
    sorters: SORTERS,
    defaultSort: 'maturity',
    urlKey: 'd',
  });

  const sortOptions = useMemo(() => [
    { id: 'maturity', label: t('portfolio.fixedIncome.controls.sortMaturity') },
    { id: 'pnl', label: t('portfolio.fixedIncome.controls.sortPnl') },
    { id: 'value', label: t('portfolio.fixedIncome.controls.sortValue') },
    { id: 'start', label: t('portfolio.fixedIncome.controls.sortStart') },
  ], [t]);

  if (!portfolioId) return null;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 min-w-0">
          <Landmark className="h-4 w-4 text-accent shrink-0" />
          <h3 className="text-sm font-semibold text-fg truncate">{t('deposits.title')}</h3>
          {controls.total > 0 && (
            <span className="text-[11px] font-mono text-fg-muted">{controls.total}</span>
          )}
        </div>
        <Button variant="primary" size="sm" leftIcon={<Plus className="h-3.5 w-3.5" />} onClick={() => setAddOpen(true)}>
          {t('deposits.addAction')}
        </Button>
      </div>

      {!isLoading && deposits.length > 0 && (
        <FixedIncomeListControls
          controls={controls}
          sortOptions={sortOptions}
          searchPlaceholder={t('portfolio.fixedIncome.controls.searchDeposits')}
          closedLabel={t('portfolio.positions.statusClosed')}
          idPrefix="deposit"
        />
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Spinner size="md" tone="accent" />
        </div>
      ) : deposits.length === 0 ? (
        <EmptyState
          icon={<Landmark className="h-8 w-8 text-fg-muted" />}
          message={t('deposits.empty')}
          hint={t('deposits.emptyHint')}
          action={{ icon: Plus, label: t('deposits.addAction'), onClick: () => setAddOpen(true) }}
        />
      ) : controls.pageItems.length === 0 ? (
        <EmptyState
          icon={<Landmark className="h-8 w-8 text-fg-muted" />}
          message={t('portfolio.fixedIncome.controls.noMatch')}
        />
      ) : (
        <div className="space-y-3">
          <motion.div
            variants={containerVariants(0.04)}
            initial="hidden"
            animate="show"
            className="space-y-3"
          >
            {controls.pageItems.map((deposit) => (
              <DepositRow key={deposit.id} deposit={deposit} portfolioId={portfolioId} />
            ))}
          </motion.div>
          <Pagination page={controls.page} totalPages={controls.totalPages} onPageChange={controls.setPage} />
        </div>
      )}

      {addOpen && (
        <DepositFormModal
          mode="add"
          portfolioId={portfolioId}
          portfolioPicker={portfolioPicker}
          onClose={() => setAddOpen(false)}
        />
      )}
    </div>
  );
}
