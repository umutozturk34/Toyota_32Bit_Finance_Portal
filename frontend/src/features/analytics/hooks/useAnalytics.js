import { useMutation, useQuery } from '@tanstack/react-query';
import { analyticsService } from '../services/analyticsService';

export function useScenarioSimulation() {
  return useMutation({
    mutationFn: analyticsService.simulate,
  });
}

export function useInflationBeaters(period, benchmark, targetCurrency) {
  return useQuery({
    queryKey: ['analytics', 'inflation-beaters', period, benchmark || 'default', targetCurrency || 'auto'],
    queryFn: () => analyticsService.inflationBeaters(period, benchmark, targetCurrency),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}
