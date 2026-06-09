import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Wallet } from 'lucide-react';
import PositionFormModal from './PositionFormModal';
import { usePortfolioList } from '../hooks/usePortfolioData';
import useAppStore from '../../../shared/stores/useAppStore';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';

export default function MarketAddPositionModal({ assetType, assetCode, assetName, assetImage, currentPrice, onClose, onComplete }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: portfolios, isLoading } = usePortfolioList();
  const activePortfolioId = useAppStore((s) => s.activePortfolioId);
  const setActivePortfolioId = useAppStore((s) => s.setActivePortfolioId);
  const [chosenId, setChosenId] = useState(null);

  if (isLoading) return null;

  const resolvedId = chosenId
    ?? portfolios?.find((p) => String(p.id) === String(activePortfolioId))?.id
    ?? portfolios?.[0]?.id
    ?? null;

  if (!resolvedId) {
    return (
      <BaseModal
        isOpen
        onClose={onClose}
        icon={Wallet}
        title={t('marketAddPosition.noPortfolioTitle')}
        subtitle={t('marketAddPosition.noPortfolioHint')}
        size="sm"
      >
        <div className="flex justify-center pt-2">
          <Button
            variant="primary"
            size="md"
            leftIcon={<Wallet className="h-4 w-4" />}
            onClick={() => { onClose(); navigate('/portfolio'); }}
          >
            {t('marketAddPosition.goToPortfolio')}
          </Button>
        </div>
      </BaseModal>
    );
  }

  const portfolioPicker = portfolios.length > 1 ? (
    <div className="space-y-1.5">
      <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
        <Wallet className="h-3 w-3" />
        {t('marketAddPosition.choosePortfolio')}
      </label>
      <select
        value={String(resolvedId)}
        onChange={(e) => { const id = Number(e.target.value); setChosenId(id); setActivePortfolioId(id); }}
        className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg outline-none focus:ring-1 focus:ring-accent/50 transition-all cursor-pointer"
      >
        {portfolios.map((p) => (
          <option key={p.id} value={p.id}>{p.name}</option>
        ))}
      </select>
    </div>
  ) : null;

  return (
    <PositionFormModal
      mode="add"
      portfolioId={resolvedId}
      portfolioPicker={portfolioPicker}
      asset={{ type: assetType, code: assetCode, name: assetName, image: assetImage, currentPrice }}
      onClose={onClose}
      onComplete={onComplete}
    />
  );
}
