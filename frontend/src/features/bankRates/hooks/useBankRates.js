import { useQuery } from '@tanstack/react-query';
import { bankRatesService } from '../services/bankRatesService';
import { STALE } from '../../../shared/constants/query';

export function useBankRates({ currency, kind = 'CURRENCY' } = {}) {
  return useQuery({
    queryKey: ['bankRates', kind, currency || 'all'],
    queryFn: () => bankRatesService.list({ currency, kind }),
    staleTime: STALE.MEDIUM,
    placeholderData: (prev) => prev,
  });
}

export function useBankRateCurrencies({ kind = 'CURRENCY' } = {}) {
  return useQuery({
    queryKey: ['bankRateCurrencies', kind],
    queryFn: () => bankRatesService.listCurrencies({ kind }),
    staleTime: STALE.LONG,
  });
}
