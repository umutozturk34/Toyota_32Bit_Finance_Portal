import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userLayoutService } from '../services/userLayoutService';
import { useAuth } from '../../features/auth/AuthContext';

const LAYOUT_KEY = ['userLayout'];
const SCHEMA_VERSION = 2;

export const DEFAULT_OVERVIEW_LAYOUT = Object.freeze({
  schemaVersion: SCHEMA_VERSION,
  sections: [
    { sectionId: 'asset-cards-default', kind: 'ASSET_CARDS', visible: true,  order: 0, config: { assetCodes: [] } },
    { sectionId: 'movers-stock',        kind: 'MOVERS',      visible: true,  order: 1, config: { market: 'STOCK' } },
    { sectionId: 'movers-crypto',       kind: 'MOVERS',      visible: true,  order: 2, config: { market: 'CRYPTO' } },
    { sectionId: 'movers-forex',        kind: 'MOVERS',      visible: true,  order: 3, config: { market: 'FOREX' } },
    { sectionId: 'movers-fund',         kind: 'MOVERS',      visible: true,  order: 4, config: { market: 'FUND' } },
    { sectionId: 'movers-commodity',    kind: 'MOVERS',      visible: true,  order: 5, config: { market: 'COMMODITY' } },
    { sectionId: 'watchlist-default',   kind: 'WATCHLIST',   visible: true,  order: 6, config: {} },
    { sectionId: 'news',                kind: 'NEWS',        visible: true,  order: 7, config: {} },
  ],
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

function migrate(section) {
  const sectionId = section.sectionId === 'bist-indices' ? 'asset-cards-default' : section.sectionId;
  return {
    sectionId,
    kind: section.kind || inferKind(sectionId),
    visible: section.visible !== false,
    order: typeof section.order === 'number' ? section.order : 0,
    config: section.config && typeof section.config === 'object' ? section.config : {},
  };
}

function normalize(layout) {
  if (!layout || !Array.isArray(layout.sections) || layout.sections.length === 0) {
    return DEFAULT_OVERVIEW_LAYOUT;
  }
  const migrated = layout.sections.map(migrate).filter((s) => s.kind);
  const presentIds = new Set(migrated.map((s) => s.sectionId));
  const presentKinds = new Set(migrated.map((s) => s.kind));
  const singletonKinds = new Set(['ASSET_CARDS', 'NEWS', 'WATCHLIST']);
  let nextOrder = migrated.reduce((max, s) => Math.max(max, s.order), -1) + 1;
  for (const def of DEFAULT_OVERVIEW_LAYOUT.sections) {
    if (presentIds.has(def.sectionId)) continue;
    if (singletonKinds.has(def.kind) && presentKinds.has(def.kind)) continue;
    migrated.push({ ...def, order: nextOrder++ });
  }
  migrated.sort((a, b) => a.order - b.order);
  return { schemaVersion: SCHEMA_VERSION, sections: migrated };
}

export function useUserLayout() {
  const { isAuthenticated, loading } = useAuth();
  const query = useQuery({
    queryKey: LAYOUT_KEY,
    queryFn: userLayoutService.get,
    staleTime: Infinity,
    retry: false,
    enabled: isAuthenticated && !loading,
  });
  const overview = normalize(query.data?.overview);
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
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: LAYOUT_KEY });
      queryClient.invalidateQueries({ queryKey: ['marketOverview'] });
    },
  });
}
