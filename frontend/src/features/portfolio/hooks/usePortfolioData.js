import { useCallback, useEffect, useState } from 'react';
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
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
  });
}

export function usePortfolioLimits() {
  return useQuery({
    queryKey: ['portfolioLimits'],
    queryFn: portfolioService.getLimits,
    staleTime: STALE.LONG,
    refetchOnWindowFocus: false,
  });
}

const EMPTY_BACKFILL = { running: false, since: null, pendingKeys: new Set() };

const lotKey = (assetType, assetCode) => `${assetType}:${assetCode}`;

export function useBackfillStatus(portfolioId) {
  const [state, setState] = useState(EMPTY_BACKFILL);
  const invalidate = useInvalidateAfterBackfill();

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
        } catch { void 0; }
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
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
  });
}

export function usePortfolioSummary(portfolioId, assetType) {
  return useQuery({
    queryKey: ['portfolioSummary', portfolioId, assetType],
    queryFn: () => portfolioService.getSummary(portfolioId, assetType),
    enabled: !!portfolioId && !!assetType,
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
  });
}

export function usePortfolioAllocation(portfolioId, mode, assetType) {
  return useQuery({
    queryKey: ['portfolioAllocation', portfolioId, mode, assetType],
    queryFn: () => portfolioService.getAllocation(portfolioId, mode, assetType),
    enabled: !!portfolioId,
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
  });
}

export function usePortfolioPerformance(portfolioId, range, assetType) {
  return useQuery({
    queryKey: ['portfolioPerformance', portfolioId, range, assetType],
    queryFn: () => portfolioService.getPerformance(portfolioId, range, assetType),
    enabled: !!portfolioId,
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
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
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
  });
}

function rateLimitAwareRetry(failureCount, error) {
  if (error?.response?.status === 429) return failureCount < 3;
  if (failureCount === 0) return true;
  return false;
}

function rateLimitAwareDelay(failureCount, error) {
  if (error?.response?.status === 429) {
    const headerSecs = error.response?.headers?.['x-rate-limit-retry-after-seconds']
      ?? error.response?.headers?.['retry-after'];
    const secs = Number(headerSecs);
    if (Number.isFinite(secs) && secs > 0) return Math.min(secs * 1000, 30_000);
    return Math.min(2000 * (failureCount + 1), 10_000);
  }
  return 1000 * Math.pow(2, failureCount);
}

export function usePortfolioPositions(portfolioId, params) {
  return useQuery({
    queryKey: ['portfolioPositions', portfolioId, params],
    queryFn: () => portfolioService.getPositions(portfolioId, params),
    enabled: !!portfolioId,
    placeholderData: (prev) => prev,
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
    retry: rateLimitAwareRetry,
    retryDelay: rateLimitAwareDelay,
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
  return useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['portfolios'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioView'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioSummary'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioAllocation'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioPerformance'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioPositions'] });
    queryClient.invalidateQueries({ queryKey: ['assetSeries'] });
  }, [queryClient]);
}

export function useInvalidateAfterBackfill() {
  const queryClient = useQueryClient();
  return useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['portfolioView'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioPerformance'] });
    queryClient.invalidateQueries({ queryKey: ['assetSeries'] });
  }, [queryClient]);
}
