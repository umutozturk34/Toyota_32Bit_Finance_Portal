import { useQuery } from '@tanstack/react-query';
import { unifiedMarketService } from '../services/unifiedMarketService';
import { STALE, GC } from '../constants/query';

const FALLBACK_RATES = { TRY: 1, USD: null, EUR: null };

export function useExchangeRates() {
  const { data } = useQuery({
    queryKey: ['exchangeRates'],
    queryFn: async () => {
      const paged = await unifiedMarketService.search({ type: 'FOREX', size: 100 });
      const content = paged?.content || [];
      const priceOf = (code) => {
        const match = content.find((c) => c.code === code);
        const value = match?.price != null ? Number(match.price) : null;
        return Number.isFinite(value) && value > 0 ? value : null;
      };
      return { TRY: 1, USD: priceOf('USD'), EUR: priceOf('EUR') };
    },
    staleTime: STALE.MEDIUM,
    gcTime: GC.LONG,
  });
  return data || FALLBACK_RATES;
}
