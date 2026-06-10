import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const useAppStore = create(
  persist(
    (set, get) => ({
      sidebarCollapsed: false,
      cooldowns: JSON.parse(sessionStorage.getItem('cooldowns') || '{}'),
      activeWatchlistId: null,
      activePortfolioId: null,
      chartSidebarOpen: false,
      chartActiveTab: 'indicators',
      displayCurrency: 'ORIGINAL',
      searchOpen: false,

      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),

      openSearch: () => set({ searchOpen: true }),
      closeSearch: () => set({ searchOpen: false }),
      toggleSearch: () => set((s) => ({ searchOpen: !s.searchOpen })),

      setDisplayCurrency: (currency) => set({ displayCurrency: currency }),

      setCooldown: (route, endTime) =>
        set((s) => {
          const next = { ...s.cooldowns, [route]: endTime };
          try { sessionStorage.setItem('cooldowns', JSON.stringify(next)); } catch { /* sessionStorage unavailable */ }
          return { cooldowns: next };
        }),
      getCooldownEnd: (route) => get().cooldowns[route] ?? 0,

      setActiveWatchlistId: (id) => set({ activeWatchlistId: id }),

      setActivePortfolioId: (id) => set({ activePortfolioId: id }),

      setChartSidebarOpen: (open) => set({ chartSidebarOpen: !!open }),
      setChartActiveTab: (tab) => set({ chartActiveTab: tab }),
    }),
    {
      name: 'finance-app-store',
      partialize: (state) => ({
        sidebarCollapsed: state.sidebarCollapsed,
        activeWatchlistId: state.activeWatchlistId,
        activePortfolioId: state.activePortfolioId,
        chartSidebarOpen: state.chartSidebarOpen,
        chartActiveTab: state.chartActiveTab,
        displayCurrency: state.displayCurrency,
      }),
    }
  )
);

export default useAppStore;
