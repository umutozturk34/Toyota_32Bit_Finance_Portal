import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { stockService } from '../../stock/services/stockService';

const TICKER_RE = /\(([A-Z0-9]{3,6})\)/g;

/**
 * Loads the BIST equity catalog ONCE per session (heavily cached) and indexes it by bare ticker, so news articles —
 * which cite companies by their parenthesised ticker, e.g. "(KRVGD)" — can be linked to the real tradable asset.
 * There is no stored news↔asset link, so this is the client-side bridge. Returns a Map keyed by upper-case bare
 * ticker → { code (full, e.g. "KRVGD.IS"), bare, name }.
 */
export function useAssetMentionIndex() {
  const { data } = useQuery({
    queryKey: ['assetMentionIndex', 'STOCK'],
    queryFn: async () => {
      const res = await stockService.getAll({ size: 2000 });
      return Array.isArray(res) ? res : res?.content ?? [];
    },
    staleTime: 60 * 60 * 1000,
    gcTime: 2 * 60 * 60 * 1000,
  });

  return useMemo(() => {
    const byCode = new Map();
    for (const s of data ?? []) {
      const full = s?.code;
      if (!full) continue;
      const bare = String(full).replace(/\.IS$/i, '').toUpperCase();
      if (bare.length >= 2) byCode.set(bare, { code: full, bare, name: s.name || bare });
    }
    return byCode;
  }, [data]);
}

/**
 * Up to {@code limit} assets an article names. Currently extracts parenthesised BIST tickers from the title +
 * description and keeps only those present in {@code index} — so exchange/regulator acronyms (KAP, SPK, …) and
 * other noise are dropped and every returned tag links to a genuinely tradable asset. Returns [{ code, bare, name }].
 */
export function detectAssetMentions(article, index, limit = 2) {
  if (!index || index.size === 0) return [];
  const text = `${article?.title ?? ''} ${article?.description ?? ''}`;
  const seen = new Set();
  const out = [];
  TICKER_RE.lastIndex = 0;
  let m;
  while ((m = TICKER_RE.exec(text)) !== null && out.length < limit) {
    const bare = m[1].toUpperCase();
    if (seen.has(bare)) continue;
    seen.add(bare);
    const hit = index.get(bare);
    if (hit) out.push(hit);
  }
  return out;
}
