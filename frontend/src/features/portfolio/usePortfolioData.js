import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { portfolioService } from './portfolioService';
import { getToken } from '../auth/keycloak';
import { useClearPendingLots, useMarkPendingLot } from './usePendingLots';

export function usePortfolioList() {
  return useQuery({
    queryKey: ['portfolios'],
    queryFn: portfolioService.list,
    retry: false,
  });
}

export function usePortfolioLimits() {
  return useQuery({
    queryKey: ['portfolioLimits'],
    queryFn: portfolioService.getLimits,
    staleTime: 1000 * 60 * 60,
  });
}

export function useBackfillStatus(portfolioId) {
  const [state, setState] = useState({ running: false, since: null });
  const invalidate = useInvalidatePortfolio();
  const clearPending = useClearPendingLots();

  useEffect(() => {
    if (!portfolioId) return undefined;
    let cancelled = false;
    let source;

    (async () => {
      const token = await getToken();
      if (cancelled || !token) return;
      source = new EventSourcePolyfill(`/api/v1/portfolios/${portfolioId}/backfill-stream`, {
        headers: { Authorization: `Bearer ${token}` },
        heartbeatTimeout: 60_000,
      });
      source.addEventListener('backfill-status', (event) => {
        const next = event.data === 'true' || event.data === true;
        setState((prev) => {
          if (prev.running && !next) {
            invalidate();
            clearPending();
            return { running: false, since: null };
          }
          if (!prev.running && next) return { running: true, since: Date.now() };
          return prev;
        });
      });
      source.onerror = () => source.close();
    })();

    return () => {
      cancelled = true;
      if (source) source.close();
    };
  }, [portfolioId]);

  return state;
}

export function usePortfolioView(portfolioId) {
  return useQuery({
    queryKey: ['portfolioView', portfolioId],
    queryFn: () => portfolioService.getView(portfolioId),
    enabled: !!portfolioId,
  });
}

export function usePortfolioSummary(portfolioId, assetType) {
  return useQuery({
    queryKey: ['portfolioSummary', portfolioId, assetType],
    queryFn: () => portfolioService.getSummary(portfolioId, assetType),
    enabled: !!portfolioId && !!assetType,
  });
}

export function usePortfolioAllocation(portfolioId, mode, assetType) {
  return useQuery({
    queryKey: ['portfolioAllocation', portfolioId, mode, assetType],
    queryFn: () => portfolioService.getAllocation(portfolioId, mode, assetType),
    enabled: !!portfolioId,
  });
}

export function usePortfolioPerformance(portfolioId, range, assetType) {
  return useQuery({
    queryKey: ['portfolioPerformance', portfolioId, range, assetType],
    queryFn: () => portfolioService.getPerformance(portfolioId, range, assetType),
    enabled: !!portfolioId,
    select: (data) => (data || []).map((d) => ({
      time: new Date(d.timestamp).getTime(),
      value: Number(d.totalValueTry),
      pnl: Number(d.totalPnlTry),
      pnlPercent: Number(d.pnlPercent),
      details: d.details || [],
      events: d.events || [],
    })),
  });
}

export function useAssetSeries(portfolioId, assetType, assetCode, range) {
  return useQuery({
    queryKey: ['assetSeries', portfolioId, assetType, assetCode, range],
    queryFn: () => portfolioService.getAssetSeries(portfolioId, assetType, assetCode, range),
    enabled: !!portfolioId && !!assetType && !!assetCode,
  });
}

export function usePortfolioPositions(portfolioId, params) {
  return useQuery({
    queryKey: ['portfolioPositions', portfolioId, params],
    queryFn: () => portfolioService.getPositions(portfolioId, params),
    enabled: !!portfolioId,
    placeholderData: (prev) => prev,
  });
}

export function useAddPosition(portfolioId) {
  const invalidate = useInvalidatePortfolio();
  const markPending = useMarkPendingLot();
  return useMutation({
    mutationFn: (payload) => portfolioService.addPosition(portfolioId, payload),
    onSuccess: (_data, variables) => {
      if (variables) markPending(variables.assetType, variables.assetCode);
      invalidate();
    },
  });
}

export function useUpdatePosition(portfolioId) {
  const invalidate = useInvalidatePortfolio();
  const markPending = useMarkPendingLot();
  return useMutation({
    mutationFn: ({ positionId, payload }) =>
      portfolioService.updatePosition(portfolioId, positionId, payload),
    onSuccess: (_data, variables) => {
      if (variables?.payload) markPending(variables.payload.assetType, variables.payload.assetCode);
      invalidate();
    },
  });
}

export function useDeletePosition(portfolioId) {
  const invalidate = useInvalidatePortfolio();
  return useMutation({
    mutationFn: (positionId) => portfolioService.deletePosition(portfolioId, positionId),
    onSuccess: invalidate,
  });
}

export function useCreatePortfolio() {
  const invalidate = useInvalidatePortfolio();
  return useMutation({
    mutationFn: portfolioService.create,
    onSuccess: invalidate,
  });
}

export function useInvalidatePortfolio() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ['portfolios'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioView'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioSummary'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioAllocation'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioPerformance'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioPositions'] });
    queryClient.invalidateQueries({ queryKey: ['assetSeries'] });
  };
}
