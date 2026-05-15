import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { derivativePositionService } from '../services/derivativePositionService';
import { useInvalidatePortfolio } from './usePortfolioData';
import { STALE } from '../../../shared/constants/query';

const listKey = (portfolioId, openOnly) => ['derivativePositions', portfolioId, openOnly];

export function useDerivativePositions(portfolioId, { openOnly = false } = {}) {
  return useQuery({
    queryKey: listKey(portfolioId, openOnly),
    queryFn: () => derivativePositionService.list(portfolioId, { openOnly }),
    enabled: Boolean(portfolioId),
    staleTime: STALE.SHORT,
  });
}

function useDerivativeMutation(portfolioId, mutationFn) {
  const qc = useQueryClient();
  const invalidatePortfolio = useInvalidatePortfolio();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['derivativePositions', portfolioId] });
      invalidatePortfolio();
    },
  });
}

export function useOpenDerivativePosition(portfolioId) {
  return useDerivativeMutation(portfolioId, (payload) =>
    derivativePositionService.open(portfolioId, payload));
}

export function useCloseDerivativePosition(portfolioId) {
  return useDerivativeMutation(portfolioId, ({ positionId, ...payload }) =>
    derivativePositionService.close(portfolioId, positionId, payload));
}

export function useUpdateCloseDerivativePosition(portfolioId) {
  return useDerivativeMutation(portfolioId, ({ positionId, ...payload }) =>
    derivativePositionService.updateClose(portfolioId, positionId, payload));
}

export function useUpdateDerivativePosition(portfolioId) {
  return useDerivativeMutation(portfolioId, ({ positionId, ...payload }) =>
    derivativePositionService.update(portfolioId, positionId, payload));
}

export function useReopenDerivativePosition(portfolioId) {
  return useDerivativeMutation(portfolioId, (positionId) =>
    derivativePositionService.reopen(portfolioId, positionId));
}

export function useDeleteDerivativePosition(portfolioId) {
  return useDerivativeMutation(portfolioId, (positionId) =>
    derivativePositionService.remove(portfolioId, positionId));
}
