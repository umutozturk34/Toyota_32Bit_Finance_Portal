import { create } from 'zustand';
import { portfolioService } from './portfolioService';

const usePortfolioStore = create((set, get) => ({
  portfolio: null,
  summary: null,
  positions: [],
  transactions: [],
  allocation: [],
  loading: true,
  error: null,
  needsOnboarding: false,
  activeTab: 'overview',
  selectedAsset: null,

  setActiveTab: (tab) => set({ activeTab: tab }),
  setSelectedAsset: (asset) => set({ selectedAsset: asset }),

  fetchAll: async () => {
    set({ loading: true, error: null });
    try {
      const portfolios = await portfolioService.list();
      if (!portfolios || portfolios.length === 0) {
        set({ needsOnboarding: true, loading: false });
        return;
      }
      const p = portfolios[0];
      const [summary, positions, transactions, allocation] = await Promise.all([
        portfolioService.getSummary(p.id),
        portfolioService.getPositions(p.id),
        portfolioService.getTransactions(p.id),
        portfolioService.getAllocation(p.id, 'assetType'),
      ]);
      set({
        portfolio: p,
        summary,
        positions,
        transactions,
        allocation,
        needsOnboarding: false,
        loading: false,
      });
    } catch (err) {
      if (err.response?.status === 404 || err.response?.data?.message?.includes('not found')) {
        set({ needsOnboarding: true, loading: false });
      } else {
        set({ error: err.response?.data?.message || 'Veriler yüklenirken hata oluştu', loading: false });
      }
    }
  },

  clearError: () => set({ error: null }),
}));

export default usePortfolioStore;
