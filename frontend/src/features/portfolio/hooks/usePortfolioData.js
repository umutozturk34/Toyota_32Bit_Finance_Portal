import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { portfolioService } from '../services/portfolioService';
import { getToken } from '../../auth/lib/keycloak';
import { STALE } from '../../../shared/constants/query';

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
    staleTime: STALE.LONG,
  });
}

const EMPTY_BACKFILL = { running: false, since: null, pendingKeys: new Set() };

const lotKey = (assetType, assetCode) => `${assetType}:${assetCode}`;

export function useBackfillStatus(portfolioId) {
  const [state, setState] = useState(EMPTY_BACKFILL);
  const invalidate = useInvalidatePortfolio();

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
        try {
          const payload = JSON.parse(event.data);
          const pendingKeys = new Set(
            (payload.pendingAssets || []).map((a) => lotKey(a.assetType, a.assetCode))
          );
          setState((prev) => {
            const next = {
              running: Boolean(payload.running),
              since: payload.since ?? null,
              pendingKeys,
            };
            if (prev.running && !next.running) invalidate();
            return next;
          });
        } catch { /* malformed payload */ }
      });
      source.onerror = () => source.close();
    })();

    return () => {
      cancelled = true;
      if (source) source.close();
    };
  }, [portfolioId, invalidate]);

  return state;
}

export function isLotPending(backfill, assetType, assetCode) {
  return backfill.pendingKeys?.has(lotKey(assetType, assetCode)) || false;
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
  return useMutation({
    mutationFn: (payload) => portfolioService.addPosition(portfolioId, payload),
    onSuccess: invalidate,
  });
}

export function useUpdatePosition(portfolioId) {
  const invalidate = useInvalidatePortfolio();
  return useMutation({
    mutationFn: ({ positionId, payload }) =>
      portfolioService.updatePosition(portfolioId, positionId, payload),
    onSuccess: invalidate,
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
