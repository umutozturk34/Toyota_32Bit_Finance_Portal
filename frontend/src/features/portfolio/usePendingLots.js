import { create } from 'zustand';

const lotKey = (assetType, assetCode) => `${assetType}:${assetCode}`;

const usePendingLotsStore = create((set, get) => ({
  pending: {},

  markPending(assetType, assetCode) {
    if (!assetType || !assetCode) return;
    const key = lotKey(assetType, assetCode);
    if (get().pending[key]) return;
    set((state) => ({ pending: { ...state.pending, [key]: Date.now() } }));
  },

  clearAll() {
    set({ pending: {} });
  },
}));

export function usePendingLots() {
  return usePendingLotsStore((s) => s.pending);
}

export function useMarkPendingLot() {
  return usePendingLotsStore((s) => s.markPending);
}

export function useClearPendingLots() {
  return usePendingLotsStore((s) => s.clearAll);
}

export function useIsLotPending(assetType, assetCode) {
  return usePendingLotsStore((s) => Boolean(s.pending[lotKey(assetType, assetCode)]));
}

export function useLotPendingSince(assetType, assetCode) {
  return usePendingLotsStore((s) => s.pending[lotKey(assetType, assetCode)] || null);
}
