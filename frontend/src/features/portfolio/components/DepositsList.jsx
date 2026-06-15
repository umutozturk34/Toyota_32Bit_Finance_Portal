import { useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Landmark, Plus } from 'lucide-react';
import { containerVariants } from '../../../shared/utils/animations';
import Button from '../../../shared/components/buttons/Button';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import Spinner from '../../../shared/components/feedback/Spinner';
import DepositRow from './DepositRow';
import DepositFormModal from './DepositFormModal';
import { useDeposits } from '../hooks/useFixedIncomePositions';

export default function DepositsList({ portfolioId, portfolioPicker }) {
  const { t } = useTranslation();
  const { data, isLoading } = useDeposits(portfolioId);
  const deposits = data || [];
  const [addOpen, setAddOpen] = useState(false);

  if (!portfolioId) return null;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 min-w-0">
          <Landmark className="h-4 w-4 text-accent shrink-0" />
          <h3 className="text-sm font-semibold text-fg truncate">{t('deposits.title')}</h3>
          {deposits.length > 0 && (
            <span className="text-[11px] font-mono text-fg-muted">{deposits.length}</span>
          )}
        </div>
        <Button variant="primary" size="sm" leftIcon={<Plus className="h-3.5 w-3.5" />} onClick={() => setAddOpen(true)}>
          {t('deposits.addAction')}
        </Button>
      </div>

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
      ) : (
        <motion.div
          variants={containerVariants(0.04)}
          initial="hidden"
          animate="show"
          className="space-y-3"
        >
          {deposits.map((deposit) => (
            <DepositRow key={deposit.id} deposit={deposit} portfolioId={portfolioId} />
          ))}
        </motion.div>
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
