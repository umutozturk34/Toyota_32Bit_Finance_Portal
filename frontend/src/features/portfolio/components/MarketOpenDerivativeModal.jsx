import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Wallet } from 'lucide-react';
import OpenDerivativePositionModal from './OpenDerivativePositionModal';
import { usePortfolioList } from '../hooks/usePortfolioData';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';

export default function MarketOpenDerivativeModal({ assetCode, assetName, currentPrice, metadata, onClose, onComplete }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: portfolios, isLoading } = usePortfolioList();
  const portfolioId = portfolios?.[0]?.id ?? null;

  if (isLoading) return null;

  if (!portfolioId) {
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

  return (
    <OpenDerivativePositionModal
      portfolioId={portfolioId}
      isOpen
      lockedContract={{ symbol: assetCode, name: assetName, currentPrice, metadata }}
      onClose={() => { onClose(); onComplete?.(); }}
    />
  );
}
