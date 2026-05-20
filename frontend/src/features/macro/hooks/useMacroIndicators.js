import { useQuery } from '@tanstack/react-query';
import { macroIndicatorService } from '../services/macroIndicatorService';
import { STALE } from '../../../shared/constants/query';

export function useMacroIndicators({ category, prominentOnly = false } = {}) {
  return useQuery({
    queryKey: ['macroIndicators', { category, prominentOnly }],
    queryFn: () => macroIndicatorService.list({ category, prominentOnly }),
    staleTime: STALE.LONG,
    refetchOnWindowFocus: false,
  });
}

export function useMacroIndicatorHistory(code, { from, to } = {}) {
  return useQuery({
    queryKey: ['macroIndicatorHistory', code, { from, to }],
    queryFn: () => macroIndicatorService.history(code, { from, to }),
    enabled: Boolean(code),
    staleTime: STALE.LONG,
    refetchOnWindowFocus: false,
  });
}
