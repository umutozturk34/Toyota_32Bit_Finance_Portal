import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';

// Each asset type's detail route; the resolved code is the route key (a BIST symbol or a crypto id).
const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', COMMODITY: '/commodities' };
const bareCode = (code) => String(code || '').replace(/\.IS$/i, '');

/**
 * A clickable tag for an asset a news article mentions: navigates to that asset's detail page (type-aware) and is
 * tinted by the asset's CURRENT daily move — green ↗ up, red ↘ down, accent when flat/unknown — so the reader sees
 * at a glance whether the named stock/coin is rising or falling. The live figure is fetched lazily and cached, so a
 * repeated code across the news grid shares one request.
 */
export default function AssetMentionTag({ code, type, onNavigate }) {
  const navigate = useNavigate();
  const { data } = useQuery({
    queryKey: ['assetMini', type, code],
    queryFn: () => unifiedMarketService.getByCode(type, code),
    enabled: !!code && !!type,
    staleTime: 60 * 1000,
  });

  const change = data?.changePercent;
  const up = change != null && Number(change) > 0;
  const down = change != null && Number(change) < 0;
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
      title={data?.name ? `${data.name}${change != null ? ` · ${Number(change) > 0 ? '+' : ''}${Number(change).toFixed(2)}%` : ''}` : code}
      className={`inline-flex items-center gap-0.5 rounded-md border px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider transition-[filter] hover:brightness-125 cursor-pointer ${tone}`}
    >
      {bareCode(code)}
      {up && <ArrowUpRight className="h-2.5 w-2.5" strokeWidth={2.5} />}
      {down && <ArrowDownRight className="h-2.5 w-2.5" strokeWidth={2.5} />}
    </button>
  );
}
