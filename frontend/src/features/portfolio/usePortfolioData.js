import { useQuery, useQueryClient } from '@tanstack/react-query';
import { portfolioService } from './portfolioService';

export function usePortfolioList() {
  return useQuery({
    queryKey: ['portfolios'],
    queryFn: portfolioService.list,
    retry: false,
  });
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

export function usePortfolioTransactions(portfolioId, params) {
  return useQuery({
    queryKey: ['portfolioTransactions', portfolioId, params],
    queryFn: () => portfolioService.getTransactions(portfolioId, params),
    enabled: !!portfolioId,
    placeholderData: (prev) => prev,
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
    queryClient.invalidateQueries({ queryKey: ['portfolioTransactions'] });
    queryClient.invalidateQueries({ queryKey: ['assetSeries'] });
  };
}
