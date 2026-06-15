import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Landmark, Plus } from 'lucide-react';
import { containerVariants } from '../../../shared/utils/animations';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import Spinner from '../../../shared/components/feedback/Spinner';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import Button from '../../../shared/components/buttons/Button';
import { useBonds } from '../hooks/useFixedIncomePositions';
import BondRow from './BondRow';
import BondFormModal from './BondFormModal';

export default function BondsList({ portfolioId }) {
  const { t } = useTranslation();
  const { data: bonds = [], isLoading } = useBonds(portfolioId);
  const [statusFilter, setStatusFilter] = useState('all');
  const [addOpen, setAddOpen] = useState(false);

  const visibleBonds = useMemo(() => {
    if (statusFilter === 'open') return bonds.filter((b) => !b.exitDate);
    if (statusFilter === 'closed') return bonds.filter((b) => !!b.exitDate);
    return bonds;
  }, [bonds, statusFilter]);

  if (!portfolioId) return null;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <FilterTabs
          items={[
            { type: 'open', label: t('portfolio.positions.statusOpen') },
            { type: 'closed', label: t('portfolio.bonds.statusSold') },
          ]}
          activeId={statusFilter === 'all' ? 'ALL' : statusFilter}
          onSelect={(id) => setStatusFilter(id === 'ALL' ? 'all' : id)}
          allLabel={t('portfolio.positions.statusAll')}
          showAll
          layoutId="bond-status"
        />
        <Button size="sm" leftIcon={<Plus className="h-3.5 w-3.5" />} onClick={() => setAddOpen(true)}>
          {t('portfolio.bonds.addAction')}
        </Button>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Spinner size="md" tone="accent" />
        </div>
      ) : visibleBonds.length === 0 ? (
        <EmptyState
          icon={<Landmark className="h-6 w-6 text-accent" />}
          message={t('portfolio.bonds.empty')}
          hint={t('portfolio.bonds.emptyHint')}
          action={{ label: t('portfolio.bonds.addAction'), icon: Plus, onClick: () => setAddOpen(true) }}
        />
      ) : (
        <div className="space-y-3">
          <motion.div variants={containerVariants(0.05)} initial="hidden" animate="show" className="space-y-3">
            <AnimatePresence mode="popLayout">
              {visibleBonds.map((bond) => (
                <BondRow key={bond.id} portfolioId={portfolioId} bond={bond} />
              ))}
            </AnimatePresence>
          </motion.div>
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
