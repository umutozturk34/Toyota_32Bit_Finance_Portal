import { useMemo } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { analyticsService } from '../services/analyticsService';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { ANALYTICS_TO_MARKET_TYPE } from '../returnsConstants';

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

export function useAssetReturns() {
  return useQuery({
    queryKey: ['analytics', 'asset-returns'],
    queryFn: analyticsService.assetReturns,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}

// The analytics rankings (returns + inflation-beater) carry only codes/ids, so pull the proper display names
// (long company/index names) and crypto logos from the market endpoint, paginated to completion (page 0, then
// the remaining pages in parallel) and cached. Crypto fits one page; stocks span a few.
async function fetchAllAssets(type) {
  const first = await unifiedMarketService.search({ type, page: 0, size: 100 });
  const content = first?.content ?? [];
  const total = first?.totalElements ?? content.length;
  const pages = Math.ceil(total / 100);
  if (pages <= 1) return content;
  const rest = await Promise.all(
    Array.from({ length: pages - 1 }, (_, i) => unifiedMarketService.search({ type, page: i + 1, size: 100 })),
  );
  return rest.reduce((acc, r) => acc.concat(r?.content ?? []), content);
}

/**
 * Shared display-meta lookup for the Returns and Inflation-Beater rankings. Fetches the crypto and stock
 * catalogues once (long names + crypto logos), caches them, and returns a {@code metaFor(type, code)} resolver
 * that maps an analytics instrument type to its market type before keying in — so both pages render the same
 * names and icons without each re-implementing the fetch. Forex/commodity resolve through instrumentDisplayName
 * upstream and funds stay on their code, so only crypto and stock are enriched here.
 */
export function useAssetDisplayMeta() {
  const { data: cryptoAssets } = useQuery({
    queryKey: ['rankingAssetMeta', 'CRYPTO'], queryFn: () => fetchAllAssets('CRYPTO'), staleTime: 30 * 60 * 1000,
  });
  const { data: stockAssets } = useQuery({
    queryKey: ['rankingAssetMeta', 'STOCK'], queryFn: () => fetchAllAssets('STOCK'), staleTime: 30 * 60 * 1000,
  });
  return useMemo(() => {
    const map = new Map();
    for (const asset of cryptoAssets ?? []) map.set(`CRYPTO|${asset.code}`, asset);
    for (const asset of stockAssets ?? []) map.set(`STOCK|${asset.code}`, asset);
    return (type, code) => map.get(`${ANALYTICS_TO_MARKET_TYPE[type] || type}|${code}`);
  }, [cryptoAssets, stockAssets]);
}
