import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userLayoutService } from '../services/userLayoutService';
import { useAuth } from '../../features/auth/useAuth';
import { STALE, GC } from '../constants/query';

const LAYOUT_KEY = ['userLayout'];
const SCHEMA_VERSION = 4;
const DEFAULT_PAGE_ID = 'page-1';
export const DEFAULT_PAGE_NAME_SEED = 'Anasayfa';
const MAX_PAGES = 5;

const DEFAULT_SECTIONS = Object.freeze([
  { sectionId: 'asset-cards-default', kind: 'ASSET_CARDS', x: 0, y: 0,  w: 8, h: 3, config: {} },
  { sectionId: 'news-default',        kind: 'NEWS',        x: 8, y: 0,  w: 4, h: 21, config: {} },
  { sectionId: 'movers-stock',        kind: 'MOVERS',      x: 0, y: 3,  w: 4, h: 6, config: { market: 'STOCK' } },
  { sectionId: 'movers-crypto',       kind: 'MOVERS',      x: 0, y: 9,  w: 4, h: 6, config: { market: 'CRYPTO' } },
  { sectionId: 'movers-forex',        kind: 'MOVERS',      x: 4, y: 3,  w: 4, h: 6, config: { market: 'FOREX' } },
  { sectionId: 'movers-fund',         kind: 'MOVERS',      x: 4, y: 9,  w: 4, h: 6, config: { market: 'FUND' } },
  { sectionId: 'movers-commodity',    kind: 'MOVERS',      x: 0, y: 15, w: 4, h: 6, config: { market: 'COMMODITY' } },
]);

export const DEFAULT_OVERVIEW_LAYOUT = Object.freeze({
  schemaVersion: SCHEMA_VERSION,
  pages: [{ id: DEFAULT_PAGE_ID, name: 'Anasayfa', sections: DEFAULT_SECTIONS.map((s) => ({ ...s })) }],
});

const LEGACY_KIND_BY_PREFIX = {
  'asset-cards': 'ASSET_CARDS',
  'bist-indices': 'ASSET_CARDS',
  'movers': 'MOVERS',
  'watchlist': 'WATCHLIST',
  'news': 'NEWS',
};

function inferKind(sectionId) {
  if (!sectionId) return null;
  const matched = Object.entries(LEGACY_KIND_BY_PREFIX)
    .find(([prefix]) => sectionId === prefix || sectionId.startsWith(`${prefix}-`));
  return matched ? matched[1] : null;
}

const DEFAULT_SIZES = {
  ASSET_CARDS: { w: 8, h: 3 },
  MOVERS: { w: 4, h: 6 },
  WATCHLIST: { w: 4, h: 6 },
  NEWS: { w: 4, h: 14 },
  BENCHMARK_BEATERS: { w: 4, h: 10 },
};

function migrate(section, fallbackIndex) {
  const sectionId = section.sectionId === 'bist-indices' ? 'asset-cards-default' : section.sectionId;
  const kind = section.kind || inferKind(sectionId);
  if (!kind) return null;
  const def = DEFAULT_SIZES[kind] || { w: 4, h: 6 };
  const orderFallback = typeof section.order === 'number' ? section.order : fallbackIndex;
  return {
    sectionId,
    kind,
    x: typeof section.x === 'number' ? section.x : (orderFallback * def.w) % 12,
    y: typeof section.y === 'number' ? section.y : Math.floor(orderFallback / Math.max(1, Math.floor(12 / def.w))) * def.h,
    w: typeof section.w === 'number' ? section.w : def.w,
    h: typeof section.h === 'number' ? section.h : def.h,
    config: section.config && typeof section.config === 'object' ? section.config : {},
  };
}

function dedupKey(section) {
  if (section.kind === 'ASSET_CARDS') return `ASSET_CARDS:${section.sectionId}`;
  if (section.kind === 'WATCHLIST') return `WATCHLIST:${section.config?.watchlistId ?? 'default'}`;
  if (section.kind === 'MOVERS') return `MOVERS:${section.config?.market ?? 'any'}`;
  return section.kind;
}

function normalizeSections(rawSections) {
  if (!Array.isArray(rawSections)) return [];
  const seen = new Map();
  let assetCardCount = 0;
  rawSections
    .filter((s) => s.visible !== false)
    .map((s, i) => migrate(s, i))
    .filter(Boolean)
    .forEach((s) => {
      if (s.kind === 'ASSET_CARDS') {
        if (assetCardCount >= 4) return;
        assetCardCount++;
      }
      const key = dedupKey(s);
      if (!seen.has(key)) seen.set(key, s);
    });
  return [...seen.values()];
}

function normalizePage(rawPage, fallbackIndex) {
  const id = (rawPage?.id && typeof rawPage.id === 'string') ? rawPage.id : `page-${fallbackIndex + 1}`;
  const name = (rawPage?.name && typeof rawPage.name === 'string') ? rawPage.name : `Sayfa ${fallbackIndex + 1}`;
  return { id, name, sections: normalizeSections(rawPage?.sections) };
}

function normalize(layout) {
  if (!layout || typeof layout !== 'object') return DEFAULT_OVERVIEW_LAYOUT;
  if (Array.isArray(layout.pages) && layout.pages.length > 0) {
    const pages = layout.pages.slice(0, MAX_PAGES).map((p, i) => normalizePage(p, i));
    if (pages.length === 0) return DEFAULT_OVERVIEW_LAYOUT;
    return { schemaVersion: SCHEMA_VERSION, pages };
  }
  if (Array.isArray(layout.sections)) {
    const sections = normalizeSections(layout.sections);
    if (sections.length === 0) return DEFAULT_OVERVIEW_LAYOUT;
    return {
      schemaVersion: SCHEMA_VERSION,
      pages: [{ id: DEFAULT_PAGE_ID, name: 'Anasayfa', sections }],
    };
  }
  return DEFAULT_OVERVIEW_LAYOUT;
}

export function useUserLayout() {
  const { isAuthenticated, loading } = useAuth();
  const query = useQuery({
    queryKey: LAYOUT_KEY,
    queryFn: userLayoutService.get,
    staleTime: STALE.NEVER,
    gcTime: GC.NEVER,
    enabled: isAuthenticated && !loading,
  });
  const sourceOverview = query.data?.overview;
  const overview = useMemo(
    () => (query.isSuccess ? normalize(sourceOverview) : null),
    [query.isSuccess, sourceOverview],
  );
  return { ...query, overview };
}

export function useUpdateOverviewLayout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: userLayoutService.saveOverview,
    onMutate: async (overview) => {
      await queryClient.cancelQueries({ queryKey: LAYOUT_KEY });
      const previous = queryClient.getQueryData(LAYOUT_KEY);
      queryClient.setQueryData(LAYOUT_KEY, { ...(previous ?? {}), overview });
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous !== undefined) queryClient.setQueryData(LAYOUT_KEY, context.previous);
    },
    onSuccess: async (data) => {
      if (data) queryClient.setQueryData(LAYOUT_KEY, data);
      await queryClient.refetchQueries({ queryKey: ['marketOverview'] });
    },
  });
}

export { MAX_PAGES, DEFAULT_PAGE_ID, SCHEMA_VERSION };
