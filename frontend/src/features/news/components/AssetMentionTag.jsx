import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';

// Each asset type's detail route; the resolved code is the route key (a BIST symbol or a crypto id).
const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', COMMODITY: '/commodities' };
const bareCode = (code) => String(code || '').replace(/\.IS$/i, '');

/**
 * The asset's daily % move ON {@code dateStr} (the news's own date) — NOT today's live figure, since a 3-day-old
 * article tagged with today's move would mislead. Computed from the cached candle history: the close on the latest
 * trading day ≤ the news date vs the prior close (falling back to that day's open→close intraday move at the series
 * start). Null when no candle covers the date.
 */
function changeOnDate(candles, dateStr) {
  if (!Array.isArray(candles) || candles.length === 0 || !dateStr) return null;
  const day = String(dateStr).slice(0, 10);
  let idx = -1;
  for (let i = 0; i < candles.length; i += 1) {
    const d = String(candles[i].candleDate ?? candles[i].date ?? '').slice(0, 10);
    if (!d) continue;
    if (d <= day) idx = i;
    else break;
  }
  if (idx < 0) return null;
  const closeAt = (c) => Number(c.close ?? c.price);
  if (idx === 0) {
    const o = Number(candles[0].open);
    const c = closeAt(candles[0]);
    return o > 0 ? ((c - o) / o) * 100 : null;
  }
  const prev = closeAt(candles[idx - 1]);
  const cur = closeAt(candles[idx]);
  return prev > 0 ? ((cur - prev) / prev) * 100 : null;
}

/**
 * A clickable tag for an asset a news article mentions: navigates to that asset's detail page (type-aware) and is
 * tinted by the asset's move ON THE NEWS DATE — green ↗ up, red ↘ down, accent when flat/unknown. The history is
 * fetched once per asset and cached, so the same code across the news grid shares one request.
 */
export default function AssetMentionTag({ code, type, date, onNavigate }) {
  const navigate = useNavigate();
  const { data } = useQuery({
    queryKey: ['assetHistoryMini', type, code],
    queryFn: () => unifiedMarketService.getHistory(type, code, '1Y'),
    enabled: !!code && !!type,
    staleTime: 10 * 60 * 1000,
  });

  const candles = Array.isArray(data) ? data : data?.candles ?? [];
  const change = changeOnDate(candles, date);
  const up = change != null && change > 0;
  const down = change != null && change < 0;
  const tone = up
    ? 'border-success/30 bg-success/15 text-success'
    : down
      ? 'border-danger/30 bg-danger/15 text-danger'
      : 'border-accent/25 bg-accent/10 text-accent';

  const open = (e) => {
    e.stopPropagation();
    (onNavigate ?? navigate)(`${TYPE_ROUTES[type] || '/market'}/${encodeURIComponent(code)}`);
  };

  return (
    <button
      type="button"
      onClick={open}
      title={change != null ? `${bareCode(code)} · ${change > 0 ? '+' : ''}${change.toFixed(2)}%` : code}
      className={`inline-flex items-center gap-0.5 rounded-md border px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider transition-[filter] hover:brightness-125 cursor-pointer ${tone}`}
    >
      {bareCode(code)}
      {up && <ArrowUpRight className="h-2.5 w-2.5" strokeWidth={2.5} />}
      {down && <ArrowDownRight className="h-2.5 w-2.5" strokeWidth={2.5} />}
    </button>
  );
}
