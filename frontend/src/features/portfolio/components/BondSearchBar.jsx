import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { PlusCircle, Landmark } from 'lucide-react';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
import Card from '../../../shared/components/card';
import MarketAddBondModal from './MarketAddBondModal';
import { bondService } from '../../bond/services/bondService';

// A search/recent result only carries the series code + name, so resolve the full bond (series, ISIN, maturity,
// type) before opening the pre-filled add-bond modal. Mirrors ViopAddFromSearch in PositionSearchBar.
function BondAddFromSearch({ asset, onClose }) {
  const { data: bond, isLoading, isError } = useQuery({
    queryKey: ['bond-detail-search', asset.code],
    queryFn: () => bondService.getBondByCode(asset.code),
    staleTime: 30_000,
  });
  // getBondByCode returns null (not a throw) on a stale/removed series; close from an effect so the parent's
  // setState is not invoked during this child's render.
  const missing = !isLoading && (isError || !bond);
  useEffect(() => {
    if (missing) onClose();
  }, [missing, onClose]);
  if (isLoading || missing) return null;
  return <MarketAddBondModal bond={bond} onClose={onClose} onComplete={onClose} />;
}

// The tahvil (fixed-income) counterpart of PositionSearchBar: search Treasury bond series and add a recently
// viewed or freshly searched bond straight to this portfolio. filterType="BOND" keeps both the recent panel and
// the live results scoped to bonds — the only searchable instrument a Deposit & Bond portfolio can hold.
export default function BondSearchBar() {
  const { t } = useTranslation();
  const [selected, setSelected] = useState(null);
  const closeModal = () => setSelected(null);

  return (
    <Card
      as={motion.div}
      data-tour="bond-search"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      variant="elevated"
      radius="2xl"
      padding="md"
      backdropBlur
      className="relative z-50 overflow-visible"
    >
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-3">
        <div className="flex items-center gap-2">
          <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent/10 text-accent">
            <Landmark className="h-3.5 w-3.5" />
          </span>
          <div>
            <h3 className="text-sm font-semibold text-fg leading-tight">
              {t('portfolio.bonds.search.title')}
            </h3>
            <p className="text-[11px] text-fg-muted leading-tight mt-0.5">
              {t('portfolio.bonds.search.subtitle')}
            </p>
          </div>
        </div>
      </div>
      <SearchSuggestions
        placeholder={t('portfolio.bonds.search.placeholder')}
        filterType="BOND"
        secondaryAction={{
          icon: PlusCircle,
          label: t('portfolio.bonds.search.addLabel'),
          onClick: setSelected,
        }}
      />
      {selected && <BondAddFromSearch asset={selected} onClose={closeModal} />}
    </Card>
  );
}
