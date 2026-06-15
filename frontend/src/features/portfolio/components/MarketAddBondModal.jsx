import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Landmark } from 'lucide-react';
import BondFormModal from './BondFormModal';
import { usePortfolioList } from '../hooks/usePortfolioData';
import useAppStore from '../../../shared/stores/useAppStore';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';
import PortfolioSelect from '../../../shared/components/form/PortfolioSelect';

// Adds a bond to a portfolio straight from the bond MARKET page (BondDetail). Mirrors MarketAddPositionModal but
// for fixed income: a bond belongs ONLY in a FIXED (Mevduat & Tahvil) portfolio, so the picker lists FIXED
// portfolios only. The viewed bond is pre-filled and its series locked, so the user just enters quantity/price.
export default function MarketAddBondModal({ bond, onClose, onComplete }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: allPortfolios, isLoading } = usePortfolioList();
  const activePortfolioId = useAppStore((s) => s.activePortfolioId);
  const setActivePortfolioId = useAppStore((s) => s.setActivePortfolioId);
  const [chosenId, setChosenId] = useState(null);

  if (isLoading) return null;

  const portfolios = (allPortfolios || []).filter((p) => p.type === 'FIXED');

  const resolvedId = chosenId
    ?? portfolios.find((p) => String(p.id) === String(activePortfolioId))?.id
    ?? portfolios[0]?.id
    ?? null;

  if (!resolvedId) {
    return (
      <BaseModal
        isOpen
        onClose={onClose}
        icon={Landmark}
        title={t('portfolio.bonds.marketAdd.noPortfolioTitle')}
        subtitle={t('portfolio.bonds.marketAdd.noPortfolioHint')}
        size="sm"
      >
        <div className="flex justify-center pt-2">
          <Button
            variant="primary"
            size="md"
            leftIcon={<Landmark className="h-4 w-4" />}
            onClick={() => { onClose(); navigate('/portfolio'); }}
          >
            {t('marketAddPosition.goToPortfolio')}
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

  // Map the market bond onto the holding-shaped preset the form reads (series/name/isin/maturity/type, plus the
  // per-period .ORAN and the latest index for the price suggestion).
  const preset = {
    bondSeriesCode: bond.seriesCode,
    bondName: bond.name,
    bondIsin: bond.isinCode,
    maturityStart: bond.maturityStart,
    maturityEnd: bond.maturityEnd,
    bondType: bond.bondType,
    couponRate: bond.couponRate,
    baseIndex: bond.baseIndex,
  };

  return (
    <BondFormModal
      mode="add"
      portfolioId={resolvedId}
      portfolioPicker={portfolioPicker}
      bond={preset}
      lockSeries
      onClose={onClose}
      onComplete={onComplete}
    />
  );
}
