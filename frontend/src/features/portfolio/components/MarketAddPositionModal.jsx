import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Wallet } from 'lucide-react';
import PositionFormModal from './PositionFormModal';
import { usePortfolioList } from '../hooks/usePortfolioData';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';

export default function MarketAddPositionModal({ assetType, assetCode, assetName, assetImage, currentPrice, onClose, onComplete }) {
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

  return (
    <PositionFormModal
      mode="add"
      portfolioId={portfolioId}
      asset={{ type: assetType, code: assetCode, name: assetName, image: assetImage, currentPrice }}
      onClose={onClose}
      onComplete={onComplete}
    />
  );
}
