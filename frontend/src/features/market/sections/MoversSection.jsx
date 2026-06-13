import { memo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { BarChart3, ChevronRight, TrendingUp, Bitcoin, ArrowRightLeft, Briefcase } from 'lucide-react';
import { GiGoldBar } from 'react-icons/gi';

// Per-market-type header icon so the Stock / Forex / Crypto / Fund / Commodity mover panels read as distinct
// surfaces instead of five identical bar-chart glyphs differing only by colour. Forex uses the exchange
// arrows (↔), distinct from the Bank-Rates tab's banknote.
const ICON_BY_MARKET = {
  STOCK: TrendingUp,
  CRYPTO: Bitcoin,
  FOREX: ArrowRightLeft,
  FUND: Briefcase,
  COMMODITY: GiGoldBar,
};
import { getChangeClass, changeColors, formatPercent } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { priceCurrencyOf } from '../../../shared/utils/priceCurrency';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import useNavigationStore from '../../../shared/stores/useNavigationStore';
import Card from '../../../shared/components/card';
import { commodityLabel } from '../../../shared/utils/commodityName';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };

function shortLabel(asset) {
  return (asset.code || '').replace('.IS', '');
}

function AssetRow({ asset, color, onClick }) {
  const { t } = useTranslation();
  const { format: money, formatFit } = useMoney();
  const cls = getChangeClass(asset.changePercent);
  return (
    <button
      type="button"
      onClick={onClick}
      title={commodityLabel(t, asset.type, asset.code, asset.name)}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface/60 transition-colors group cursor-pointer text-left border-none bg-transparent"
    >
      {asset.image
        ? (/^https?:\/\//i.test(asset.image)
            ? <img src={asset.image} alt="" loading="lazy" className="w-5 h-5 rounded-full ring-1 ring-border-default shrink-0" />
            : <span className="w-5 h-5 rounded-full shrink-0 flex items-center justify-center text-sm leading-none">{asset.image}</span>)
        : <span
            className="w-5 h-5 rounded-full shrink-0 flex items-center justify-center text-[8px] font-bold text-white shadow-sm"
            style={{ backgroundColor: color }}
          >
            {shortLabel(asset).slice(0, 2)}
          </span>}
      <span className="font-display text-[12px] font-semibold text-fg truncate flex-1 min-w-[60px] group-hover:text-accent transition-colors">
        {shortLabel(asset)}
      </span>
      <span className="font-mono text-[11px] font-bold text-fg tabular-nums shrink-0" title={money(asset.price, priceCurrencyOf(asset))}>{formatFit(asset.price, priceCurrencyOf(asset), { maxChars: 12 })}</span>
      {asset.changePercent != null && (
        <span className={`font-mono text-[10px] font-semibold tabular-nums min-w-[48px] text-right shrink-0 ${changeColors[cls]}`}>
          {formatPercent(asset.changePercent)}
        </span>
      )}
    </button>
  );
}

function MoversSectionImpl({ data }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setOrigin = useNavigationStore((s) => s.setOrigin);
  const market = data?.market;
  const gainers = data?.gainers ?? [];
  const goToAsset = useCallback((code) => {
    setOrigin('/market', window.scrollY);
    navigate(`${TYPE_ROUTES[market] ?? '/market'}/${code}`, { state: { from: '/market' } });
  }, [navigate, setOrigin, market]);
  const losers = data?.losers ?? [];
  const color = ASSET_TYPE_COLORS[market] || '#6366f1';
  const TypeIcon = ICON_BY_MARKET[market] || BarChart3;
  const label = market ? t(`assets.labels.${market}`, { defaultValue: market }) : t('moversSection.fallback');

  return (
    <Card as="section" accentBar={color} radius="xl" padding="none" interactive={false} className="group h-full flex flex-col">
      <button
        type="button"
        onClick={() => navigate(TYPE_ROUTES[market] ?? '/market')}
        className="flex items-center gap-2 w-full p-3 cursor-pointer hover:bg-surface/30 transition-colors group/title bg-transparent border-x-0 border-t-0 border-b border-border-default"
      >
        <span
          className="flex items-center justify-center w-7 h-7 rounded-lg transition-transform duration-300 group-hover/title:scale-105"
          style={{ backgroundColor: `${color}18`, boxShadow: `0 0 16px ${color}15` }}
        >
          <TypeIcon className="h-3.5 w-3.5" style={{ color }} />
        </span>
        <span className="font-display text-[13px] font-bold text-fg">{label}</span>
        <ChevronRight className="h-3.5 w-3.5 text-fg-subtle ml-auto opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
      </button>
      <div className="grid grid-cols-2 divide-x divide-border-default flex-1 min-h-0">
        <div className="p-2 flex flex-col min-h-0">
          <div className="flex items-center gap-1.5 px-2 pb-1.5 mb-1 shrink-0">
            <span className="relative w-1.5 h-1.5">
              <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-40" />
              <span className="relative block w-1.5 h-1.5 rounded-full bg-success" />
            </span>
            <span className="font-mono text-[9px] uppercase tracking-[0.18em] text-success font-semibold">{t('moversSection.gainers')}</span>
          </div>
          <div className="space-y-0.5 overflow-y-auto scrollbar-auto-hide">
            {gainers.length === 0
              ? <p className="text-[10px] text-fg-subtle px-2 py-3 text-center">{t('moversSection.noGainers')}</p>
              : gainers.map((a) => <AssetRow key={a.code} asset={a} color={color} onClick={() => goToAsset(a.code)} />)}
          </div>
        </div>
        <div className="p-2 flex flex-col min-h-0">
          <div className="flex items-center gap-1.5 px-2 pb-1.5 mb-1 shrink-0">
            <span className="relative w-1.5 h-1.5">
              <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-40" />
              <span className="relative block w-1.5 h-1.5 rounded-full bg-danger" />
            </span>
            <span className="font-mono text-[9px] uppercase tracking-[0.18em] text-danger font-semibold">{t('moversSection.losers')}</span>
          </div>
          <div className="space-y-0.5 overflow-y-auto scrollbar-auto-hide">
            {losers.length === 0
              ? <p className="text-[10px] text-fg-subtle px-2 py-3 text-center">{t('moversSection.noLosers')}</p>
              : losers.map((a) => <AssetRow key={a.code} asset={a} color={color} onClick={() => goToAsset(a.code)} />)}
          </div>
        </div>
      </div>
    </Card>
  );
}

export default memo(MoversSectionImpl);
