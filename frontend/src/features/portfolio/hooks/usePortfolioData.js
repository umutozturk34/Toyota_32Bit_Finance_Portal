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
    let retryTimer;
    let attempt = 0;

    const connect = async () => {
      const token = await getToken();
      if (cancelled || !token) return;
      source = new EventSourcePolyfill(`/api/v1/portfolios/${portfolioId}/backfill-stream`, {
        headers: { Authorization: `Bearer ${token}` },
        heartbeatTimeout: 60_000,
      });
      source.addEventListener('backfill-status', (event) => {
        attempt = 0;
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
            // Refetch when a backfill finishes: either the global running flag clears, OR an asset that
            // was pending is no longer pending. The latter catches a fast backfill whose running:true was
            // never observed by this client (a freshly-added asset finishing between two events), which
            // otherwise left the performance chart / asset series stale until a manual refresh.
            const wentIdle = prev.running && !next.running;
            const assetFinished = prev.pendingKeys.size > 0
              && [...prev.pendingKeys].some((k) => !pendingKeys.has(k));
            if (wentIdle || assetFinished) invalidate();
            return next;
          });
        } catch { void 0; }
      });
      source.onerror = () => {
        source.close();
        if (cancelled) return;
        // Reconnect with capped exponential backoff: a dropped stream (idle proxy timeout, network
        // blip) otherwise leaves the "preparing data" banner stuck because the terminal running:false
        // event never arrives. Backoff resets to 0 on any delivered event.
        const delay = Math.min(1000 * 2 ** attempt, 15_000);
        attempt += 1;
        retryTimer = setTimeout(connect, delay);
      };
    };

    connect();

    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
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

export function usePortfolioAllocation(portfolioId, mode, assetType, limit) {
  return useQuery({
    queryKey: ['portfolioAllocation', portfolioId, mode, assetType, limit ?? null],
    queryFn: () => portfolioService.getAllocation(portfolioId, mode, assetType, limit),
    enabled: !!portfolioId,
    // Charts are switched/filtered far more often than every 30s; MEDIUM (60s) reuses cached data across a
    // browsing window instead of re-marking it stale on each switch. Add/sell/backfill still invalidate explicitly.
    staleTime: STALE.MEDIUM,
    refetchOnWindowFocus: false,
  });
}

export function usePortfolioPerformance(portfolioId, range, assetType) {
  return useQuery({
    queryKey: ['portfolioPerformance', portfolioId, range, assetType],
    queryFn: () => portfolioService.getPerformance(portfolioId, range, assetType),
    enabled: !!portfolioId,
    staleTime: STALE.MEDIUM,   // reuse cached series across tab/range/type switches (see usePortfolioAllocation)
    refetchOnWindowFocus: false,
    select: (data) => (data || []).map((d) => {
      const total = Number(d.totalPnlTry);
      const closed = Number(d.cashTry ?? 0);
      return {
        time: new Date(d.timestamp).getTime(),
        value: Number(d.totalValueTry),
        cash: closed,
        pnl: total,
        open: d.openPnlTry != null ? Number(d.openPnlTry) : total - closed,
        pnlPercent: Number(d.pnlPercent),
        // Per-currency frame from the backend (entry-date-FX cost, point/exit-date-FX value, closed realized).
        // In a USD/EUR frame: total PnL = valueByCcy − costBasisByCcy (closed lots locked at exit FX, no
        // post-sale drift); closed PnL = realizedByCcy; open = total − closed. Dropping them forced the chart
        // to net-and-divide and to re-value closed proceeds at today's rate.
        costBasisByCcy: d.costBasisByCcy || null,
        valueByCcy: d.valueByCcy || null,
        realizedByCcy: d.realizedByCcy || null,
        // Total PnL per currency (open + realized). Read directly so the headline is right whether or not the
        // displayed value carries closed proceeds (aggregate does, the type-filtered value line does not).
        pnlByCcy: d.pnlByCcy || null,
        details: d.details || [],
        events: d.events || [],
      };
    }),
  });
}

export function useAssetSeries(portfolioId, assetType, assetCode, range, direction = null, enabled = true) {
  return useQuery({
    queryKey: ['assetSeries', portfolioId, assetType, assetCode, range, direction],
    queryFn: () => portfolioService.getAssetSeries(portfolioId, assetType, assetCode, range, direction),
    enabled: enabled && !!portfolioId && !!assetType && !!assetCode,
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
  });
}

export function useAssetAggregate(portfolioId, assetType, assetCode, direction = null, enabled = true) {
  return useQuery({
    queryKey: ['assetAggregate', portfolioId, assetType, assetCode, direction],
    queryFn: () => portfolioService.getAssetAggregate(portfolioId, assetType, assetCode, direction),
    enabled: enabled && !!portfolioId && !!assetType && !!assetCode,
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

export function useAssetLots(portfolioId, assetType, assetCode) {
  return useQuery({
    queryKey: ['portfolioAssetLots', portfolioId, assetType, assetCode],
    queryFn: () => portfolioService.getPositionsByAsset(portfolioId, assetType, assetCode),
    enabled: !!portfolioId && !!assetType && !!assetCode,
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
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

export function useSellPosition(portfolioId) {
  const invalidate = useInvalidatePortfolio();
  return useMutation({
    mutationFn: ({ positionId, payload }) => portfolioService.sellPosition(portfolioId, positionId, payload),
    onSuccess: invalidate,
  });
}

export function useReopenPosition(portfolioId) {
  const invalidate = useInvalidatePortfolio();
  return useMutation({
    mutationFn: (positionId) => portfolioService.reopenPosition(portfolioId, positionId),
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

export function useRenamePortfolio() {
  const invalidate = useInvalidatePortfolio();
  return useMutation({
    mutationFn: ({ portfolioId, name }) => portfolioService.renamePortfolio(portfolioId, name),
    onSuccess: invalidate,
  });
}

export function useDeletePortfolio() {
  const invalidate = useInvalidatePortfolio();
  return useMutation({
    mutationFn: (portfolioId) => portfolioService.deletePortfolio(portfolioId),
    onSuccess: invalidate,
  });
}

export function useInvalidatePortfolio() {
  const queryClient = useQueryClient();
  return useCallback(async () => {
    await Promise.all([
      queryClient.refetchQueries({ queryKey: ['portfolios'] }),
      queryClient.refetchQueries({ queryKey: ['portfolioView'] }),
      queryClient.refetchQueries({ queryKey: ['portfolioSummary'] }),
      queryClient.refetchQueries({ queryKey: ['portfolioAllocation'] }),
      queryClient.refetchQueries({ queryKey: ['portfolioPerformance'] }),
      queryClient.refetchQueries({ queryKey: ['portfolioPositions'] }),
      queryClient.refetchQueries({ queryKey: ['portfolioAssetLots'] }),
      queryClient.refetchQueries({ queryKey: ['assetSeries'] }),
      queryClient.refetchQueries({ queryKey: ['assetAggregate'] }),
    ]);
  }, [queryClient]);
}

function useInvalidateAfterBackfill() {
  const queryClient = useQueryClient();
  return useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['portfolioView'] });
    // Backfill recomputes snapshots AND the realized/allocation aggregates + the filtered summary; without
    // these two the Dağılım / Gerçekleşen K/Z donuts and per-filter cards stayed on pre-add data until a
    // manual refresh, while the value/performance numbers had already updated — a visible mismatch.
    queryClient.invalidateQueries({ queryKey: ['portfolioSummary'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioAllocation'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioPerformance'] });
    queryClient.invalidateQueries({ queryKey: ['portfolioAssetLots'] });
    queryClient.invalidateQueries({ queryKey: ['assetSeries'] });
    queryClient.invalidateQueries({ queryKey: ['assetAggregate'] });
  }, [queryClient]);
}
