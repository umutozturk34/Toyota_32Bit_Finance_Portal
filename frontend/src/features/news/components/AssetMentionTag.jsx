import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import useDeferredVisibility from '../../../shared/hooks/useDeferredVisibility';
import { visibleDecimals } from '../../../shared/utils/formatters';

// Each asset type's detail route; the resolved code is the route key (a BIST symbol or a crypto id).
const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', COMMODITY: '/commodities', FUND: '/funds' };
const bareCode = (code) => String(code || '').replace(/\.IS$/i, '');

/**
 * A clickable tag for an asset a news article mentions: navigates to that asset's detail page (type-aware), tints
 * by the asset's move (green ↗ up, red ↘ down, accent when flat/unknown) and reveals its FULL NAME on hover. Both the
 * move and the name come from a single cached snapshot read ({@code getByCode}) — the change already sits on the
 * snapshot, so there is no need to pull a full candle history per chip (a burst that used to trip the gateway). The
 * read is deferred until in-view and deduped + cached by (type, code) across the news grid.
 */
export default function AssetMentionTag({ code, type, count, lite = false, onNavigate }) {
  const navigate = useNavigate();
  const mentions = Number(count) > 1 ? Number(count) : null;
  // Only resolve the snapshot once the chip scrolls within ~200px of the viewport. A news page mounts dozens of
  // these; deferring spreads the (cached + deduped) reads as the user scrolls instead of firing them all at once.
  const [ref, inView] = useDeferredVisibility(0, { rootMargin: '200px' });
  const { data } = useQuery({
    queryKey: ['assetSnapshot', type, code],
    queryFn: () => unifiedMarketService.getByCode(type, code),
    // `lite` (e.g. the overview widget) skips the fetch — just the clickable code, no move/name.
    enabled: !lite && inView && !!code && !!type,
    staleTime: 10 * 60 * 1000,
  });

  const name = data?.name && data.name !== bareCode(code) ? data.name : null;
  const change = data?.changePercent != null ? Number(data.changePercent) : null;
  const magnitude = change != null ? Math.abs(change) : null;
  // A sub-0.005% move rounds to "0.00" — show a flat, directionless "0%" instead of a misleading green/red arrow.
  const flat = magnitude != null && magnitude < 0.005;
  const up = magnitude != null && change > 0 && !flat;
  const down = magnitude != null && change < 0 && !flat;
  const changeTone = up ? 'text-success' : down ? 'text-danger' : 'text-fg-muted';
  const changeLabel = magnitude == null ? null : flat ? '0%' : `${magnitude.toFixed(visibleDecimals(magnitude, 2))}%`;

  const open = (e) => {
    e.stopPropagation();
    (onNavigate ?? navigate)(`${TYPE_ROUTES[type] || '/market'}/${encodeURIComponent(code)}`);
  };

  // Tooltip carries the full name (when known) + the bare code + the move, so hovering the short ticker reveals
  // which company/asset it is. The pill itself stays compact: accent CODE + a separately-tinted CHANGE segment.
  const tip = `${name ? `${name} ` : ''}(${bareCode(code)})${
    change != null ? ` · ${change > 0 ? '+' : ''}${change.toFixed(visibleDecimals(magnitude, 2))}%` : ''}`;
  return (
    <button
      ref={ref}
      type="button"
      onClick={open}
      title={tip}
      className="group/tag inline-flex items-center gap-1 rounded-md border border-border-default bg-bg-elevated/70 px-1.5 py-0.5 text-[10px] hover:border-accent/40 hover:bg-bg-elevated transition-colors cursor-pointer"
    >
      <span className="font-bold uppercase tracking-wider text-accent">{bareCode(code)}</span>
      {mentions != null && (
        <span
          className="rounded-sm bg-accent/15 px-1 font-mono text-[9px] font-bold leading-tight text-accent-bright"
          title={`${mentions}×`}
        >
          ×{mentions}
        </span>
      )}
      {changeLabel != null && (
        <span className={`inline-flex items-center gap-0.5 font-mono font-semibold ${changeTone}`}>
          {up && <ArrowUpRight className="h-2.5 w-2.5" strokeWidth={3} />}
          {down && <ArrowDownRight className="h-2.5 w-2.5" strokeWidth={3} />}
          {changeLabel}
        </span>
      )}
    </button>
  );
}
