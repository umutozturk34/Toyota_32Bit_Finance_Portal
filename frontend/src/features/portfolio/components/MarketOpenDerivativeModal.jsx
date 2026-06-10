import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Wallet } from 'lucide-react';
import OpenDerivativePositionModal from './OpenDerivativePositionModal';
import { usePortfolioList } from '../hooks/usePortfolioData';
import useAppStore from '../../../shared/stores/useAppStore';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';
import PortfolioSelect from '../../../shared/components/form/PortfolioSelect';

export default function MarketOpenDerivativeModal({ assetCode, assetName, currentPrice, metadata, onClose, onComplete }) {
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
        title={t('marketAddPosition.noPortfolioTitle', 'Portföy yok')}
        subtitle={t('marketAddPosition.noPortfolioHint', 'Önce bir portföy oluştur')}
        size="sm"
      >
        <div className="flex justify-center pt-2">
          <Button
            variant="primary"
            size="md"
            leftIcon={<Wallet className="h-4 w-4" />}
            onClick={() => { onClose(); navigate('/portfolio'); }}
          >
            {t('marketAddPosition.goToPortfolio', 'Portföye git')}
          </Button>
        </div>
      </BaseModal>
    );
  }

  const portfolioPicker = portfolios.length > 1 ? (
    <PortfolioSelect
      portfolios={portfolios}
      value={resolvedId}
      onChange={(id) => { setChosenId(id); setActivePortfolioId(id); }}
      label={t('marketAddPosition.choosePortfolio')}
    />
  ) : null;

  return (
    <OpenDerivativePositionModal
      portfolioId={resolvedId}
      portfolioPicker={portfolioPicker}
      isOpen
      lockedContract={{ symbol: assetCode, name: assetName, currentPrice, metadata }}
      onClose={() => { onClose(); onComplete?.(); }}
    />
  );
}
