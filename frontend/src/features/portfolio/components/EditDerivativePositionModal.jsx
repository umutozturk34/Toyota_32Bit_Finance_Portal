import { useQuery } from '@tanstack/react-query';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import OpenDerivativePositionModal from './OpenDerivativePositionModal';

function isoDate(value) {
  if (!value) return null;
  if (typeof value === 'string') return value.length >= 10 ? value.slice(0, 10) : value;
  try { return new Date(value).toISOString().slice(0, 10); } catch { return null; }
}

export default function EditDerivativePositionModal({ portfolioId, position, onClose }) {
  const symbol = position?.contractSymbol || position?.assetCode;
  const { data: contract, isLoading } = useQuery({
    queryKey: ['viopContract', symbol],
    queryFn: () => unifiedMarketService.getByCode('VIOP', symbol),
    enabled: Boolean(symbol),
    staleTime: 60_000,
  });

  if (isLoading || !contract) return null;

  const directionFromName = position?.assetName?.split(' · ')[0];
  const direction = directionFromName === 'SHORT' ? 'SHORT' : 'LONG';

  const editPayload = {
    id: position.id,
    direction,
    entryDate: isoDate(position.entryDate),
    entryPrice: position.entryPrice,
    quantityLot: position.quantity,
  };

  return (
    <OpenDerivativePositionModal
      portfolioId={portfolioId}
      isOpen
      onClose={onClose}
      lockedContract={contract}
      editPosition={editPayload}
    />
  );
}
