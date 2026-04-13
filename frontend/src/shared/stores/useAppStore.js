import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const useAppStore = create(
  persist(
    (set, get) => ({
      sidebarCollapsed: false,
      cooldowns: JSON.parse(sessionStorage.getItem('cooldowns') || '{}'),

      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),

      setCooldown: (route, endTime) =>
        set((s) => {
          const next = { ...s.cooldowns, [route]: endTime };
          try { sessionStorage.setItem('cooldowns', JSON.stringify(next)); } catch {}
          return { cooldowns: next };
        }),
      getCooldownEnd: (route) => get().cooldowns[route] ?? 0,
    }),
    {
      name: 'finance-app-store',
      partialize: (state) => ({
        sidebarCollapsed: state.sidebarCollapsed,
      }),
    }
  )
);

export default useAppStore;
