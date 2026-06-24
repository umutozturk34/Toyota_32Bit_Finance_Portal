import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fixedIncomeService } from '../services/fixedIncomeService';
import { useInvalidatePortfolio } from './usePortfolioData';
import { STALE } from '../../../shared/constants/query';

const depositsKey = (portfolioId) => ['depositPositions', portfolioId];
const bondsKey = (portfolioId) => ['bondPositions', portfolioId];
const summaryKey = (portfolioId) => ['fixedIncomeSummary', portfolioId];
const historyKey = (portfolioId) => ['fixedIncomeHistory', portfolioId];

export function useDeposits(portfolioId) {
  return useQuery({
    queryKey: depositsKey(portfolioId),
    queryFn: () => fixedIncomeService.deposits.list(portfolioId),
    enabled: Boolean(portfolioId),
    staleTime: STALE.SHORT,
  });
}

export function useBonds(portfolioId) {
  return useQuery({
    queryKey: bondsKey(portfolioId),
    queryFn: () => fixedIncomeService.bonds.list(portfolioId),
    enabled: Boolean(portfolioId),
    staleTime: STALE.SHORT,
  });
}

export function useBondCouponSchedule(portfolioId, bondId) {
  return useQuery({
    queryKey: ['bondCouponSchedule', portfolioId, bondId],
    queryFn: () => fixedIncomeService.bonds.couponSchedule(portfolioId, bondId),
    enabled: Boolean(portfolioId && bondId),
    staleTime: STALE.SHORT,
  });
}

export function useFixedIncomeSummary(portfolioId) {
  return useQuery({
    queryKey: summaryKey(portfolioId),
    queryFn: () => fixedIncomeService.summary(portfolioId),
    enabled: Boolean(portfolioId),
    staleTime: STALE.SHORT,
  });
}

export function useFixedIncomeHistory(portfolioId, period) {
  return useQuery({
    queryKey: [...historyKey(portfolioId), period],
    queryFn: () => fixedIncomeService.history(portfolioId, period),
    enabled: Boolean(portfolioId),
    staleTime: STALE.SHORT,
  });
}

function useFixedIncomeMutation(portfolioId, listKey, mutationFn) {
  const qc = useQueryClient();
  const invalidatePortfolio = useInvalidatePortfolio();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: listKey(portfolioId) });
      qc.invalidateQueries({ queryKey: summaryKey(portfolioId) });
      qc.invalidateQueries({ queryKey: historyKey(portfolioId) });
      invalidatePortfolio();
    },
  });
}

function useFixedIncomeDeleteMutation(portfolioId, listKey, mutationFn) {
  const qc = useQueryClient();
  const invalidatePortfolio = useInvalidatePortfolio();
  const key = listKey(portfolioId);
  return useMutation({
    mutationFn,
    // The mutationFn is invoked with the bare position id, so onMutate can drop
    // it from the cached list before the request resolves; without this the row
    // would only dim (closed styling) and re-sort until the refetch lands.
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: key });
      const previous = qc.getQueryData(key);
      qc.setQueryData(key, (rows) =>
        Array.isArray(rows) ? rows.filter((row) => row.id !== id) : rows);
      return { previous };
    },
    onError: (_err, _id, context) => {
      if (context?.previous !== undefined) {
        qc.setQueryData(key, context.previous);
      }
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: key });
      qc.invalidateQueries({ queryKey: summaryKey(portfolioId) });
      qc.invalidateQueries({ queryKey: historyKey(portfolioId) });
      invalidatePortfolio();
    },
  });
}

export function useAddDeposit(portfolioId) {
  return useFixedIncomeMutation(portfolioId, depositsKey, (payload) =>
    fixedIncomeService.deposits.add(portfolioId, payload));
}

export function useUpdateDeposit(portfolioId) {
  return useFixedIncomeMutation(portfolioId, depositsKey, ({ depositId, ...payload }) =>
    fixedIncomeService.deposits.update(portfolioId, depositId, payload));
}

export function useCloseDeposit(portfolioId) {
  return useFixedIncomeMutation(portfolioId, depositsKey, ({ depositId, ...payload }) =>
    fixedIncomeService.deposits.close(portfolioId, depositId, payload));
}

export function useReopenDeposit(portfolioId) {
  return useFixedIncomeMutation(portfolioId, depositsKey, (depositId) =>
    fixedIncomeService.deposits.reopen(portfolioId, depositId));
}

export function useDeleteDeposit(portfolioId) {
  return useFixedIncomeDeleteMutation(portfolioId, depositsKey, (depositId) =>
    fixedIncomeService.deposits.delete(portfolioId, depositId));
}

export function useAddBond(portfolioId) {
  return useFixedIncomeMutation(portfolioId, bondsKey, (payload) =>
    fixedIncomeService.bonds.add(portfolioId, payload));
}

export function useUpdateBond(portfolioId) {
  return useFixedIncomeMutation(portfolioId, bondsKey, ({ bondId, ...payload }) =>
    fixedIncomeService.bonds.update(portfolioId, bondId, payload));
}

export function useSellBond(portfolioId) {
  return useFixedIncomeMutation(portfolioId, bondsKey, ({ bondId, ...payload }) =>
    fixedIncomeService.bonds.sell(portfolioId, bondId, payload));
}

export function useReopenBond(portfolioId) {
  return useFixedIncomeMutation(portfolioId, bondsKey, (bondId) =>
    fixedIncomeService.bonds.reopen(portfolioId, bondId));
}

export function useDeleteBond(portfolioId) {
  return useFixedIncomeDeleteMutation(portfolioId, bondsKey, (bondId) =>
    fixedIncomeService.bonds.delete(portfolioId, bondId));
}
