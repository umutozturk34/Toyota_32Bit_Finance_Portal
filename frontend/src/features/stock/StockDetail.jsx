import { useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Briefcase, CalendarDays, MapPin, Landmark } from 'lucide-react';
import { stockService } from './services/stockService';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import { assetRoute } from '../watch/lib/watchConstants';
import { assetColorStyle, assetColor } from '../../shared/utils/assetColor';

function StockHeader({ asset }) {
  const displaySymbol = asset.code?.replace('.IS', '') || '';
  return (
    <>
      {asset.image ? (
        <img src={asset.image} alt={displaySymbol} className="w-8 h-8 shrink-0 rounded-full object-contain bg-white" />
      ) : (
        <span
          className="flex items-center justify-center w-8 h-8 shrink-0 rounded-full text-xs font-bold"
          style={assetColorStyle(displaySymbol)}
        >
          {displaySymbol.slice(0, 2).toUpperCase()}
        </span>
      )}
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{displaySymbol}</h1>
        <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{asset.name}</p>
      </div>
    </>
  );
}

// Company künye (sector / founding year / HQ) plus the indices the stock belongs to — each a chip linking to the
// index's own detail page (indices are tradable STOCK rows). Present only on the detail response; until the
// İş Yatırım enrichment has run this block simply renders nothing.
function StockMetadata({ asset }) {
  const { t } = useTranslation();
  const meta = asset.metadata || {};
  const indices = meta.indexMemberships || [];
  const foundedYear = meta.foundedDate ? new Date(meta.foundedDate).getFullYear() : null;

  const stats = [
    foundedYear && { label: t('marketDetail.stock.founded'), value: foundedYear, Icon: CalendarDays, color: '#fbbf24' },
    meta.city && { label: t('marketDetail.stock.city'), value: meta.city, Icon: MapPin, color: '#2dd4bf' },
    meta.exchange && { label: t('marketDetail.stock.exchange'), value: meta.exchange, Icon: Landmark, color: '#38bdf8' },
  ].filter(Boolean);

  const hasKunye = Boolean(meta.sector) || stats.length > 0;
  if (!hasKunye && !indices.length) return null;

  return (
    <div className="space-y-3">
      {hasKunye && (
        <div className="space-y-3 rounded-2xl border border-border-default bg-bg-elevated/50 p-4 backdrop-blur">
          {meta.sector && (
            <KunyeStat Icon={Briefcase} color="#818cf8" label={t('marketDetail.stock.sector')} value={meta.sector} wrap />
          )}
          {stats.length > 0 && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              {stats.map((s) => (
                <KunyeStat key={s.label} Icon={s.Icon} color={s.color} label={s.label} value={s.value} />
              ))}
            </div>
          )}
        </div>
      )}
      {indices.length > 0 && (
        <div className="rounded-2xl border border-border-default bg-bg-elevated/40 p-3.5 backdrop-blur">
          <p className="mb-2.5 text-[10px] font-bold uppercase tracking-wider text-fg-subtle">
            {t('marketDetail.stock.indices')}
          </p>
          <div className="flex flex-wrap gap-1.5">
            {indices.map((ix) => {
              const color = assetColor(ix.indexCode);
              return (
                <Link
                  key={ix.indexCode}
                  to={assetRoute('STOCK', `${ix.indexCode}.IS`)}
                  style={{ '--c': color }}
                  className="group inline-flex items-center gap-1.5 rounded-md border border-border-default bg-bg-base/40 px-2 py-1 transition-all duration-200 hover:-translate-y-0.5 hover:border-[color:var(--c)] hover:shadow-[0_6px_16px_-10px_var(--c)]"
                >
                  <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: color }} />
                  <span className="font-mono text-[11px] font-bold text-fg">{ix.indexCode}</span>
                  {ix.weight != null && (
                    <span className="font-mono text-[10px] font-bold tabular-nums" style={{ color }}>%{Number(ix.weight).toFixed(2)}</span>
                  )}
                </Link>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

// One künye stat: a colour-keyed icon + label + value. The sector wraps (it can be a long sentence) instead of
// truncating, so it stays readable; short stats (year/city/exchange) sit in a responsive row.
function KunyeStat({ Icon, color, label, value, wrap }) {
  return (
    <div className="flex items-start gap-2.5">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: `${color}22`, color }}>
        <Icon className="h-4 w-4" />
      </span>
      <div className="min-w-0">
        <p className="text-[10px] uppercase tracking-wider text-fg-subtle">{label}</p>
        <p className={`mt-0.5 text-sm font-semibold text-fg ${wrap ? 'leading-snug' : 'truncate'}`}>{value}</p>
      </div>
    </div>
  );
}

// Reverse view: when the asset is an index, the stocks that make it up — each a chip linking to its own
// detail. Sits in the right column beside the chart/news. Empty (renders nothing) for a normal stock or an
// index whose constituents have not been enriched yet.
function StockConstituents({ asset }) {
  const { t } = useTranslation();
  const constituents = asset.metadata?.constituents || [];
  if (!constituents.length) return null;
  // Heaviest holdings lead; sector-only members (null weight) sink to the end. The fill bars are normalised to
  // the top holding so the panel reads as a weight heatmap at a glance.
  const sorted = [...constituents].sort((a, b) => (Number(b.weight) || -1) - (Number(a.weight) || -1));
  const maxWeight = Math.max(...sorted.map((c) => Number(c.weight) || 0), 0.01);

  return (
    <div className="overflow-hidden rounded-2xl border border-border-default bg-bg-elevated/50 p-3.5 backdrop-blur">
      <div className="mb-3 flex items-center gap-2.5">
        <span className="h-2 w-2 rounded-full bg-accent" style={{ boxShadow: '0 0 10px var(--accent-glow, rgba(99,102,241,0.6))' }} />
        <p className="text-[11px] font-bold uppercase tracking-wider text-fg">{t('marketDetail.stock.constituents')}</p>
        <span className="rounded-full bg-surface/70 px-2 py-0.5 text-[10px] font-bold tabular-nums text-fg-muted">{constituents.length}</span>
        <span className="ml-1 h-px flex-1 bg-gradient-to-r from-border-default to-transparent" />
      </div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {sorted.map((c) => {
          const display = (c.stockSymbol || '').replace('.IS', '');
          const color = assetColor(display);
          const w = Number(c.weight);
          const hasWeight = c.weight != null && !Number.isNaN(w);
          const fillPct = hasWeight ? Math.max(6, (w / maxWeight) * 100) : 0;
          return (
            <Link
              key={c.stockSymbol}
              to={assetRoute('STOCK', c.stockSymbol)}
              style={{ '--c': color }}
              className="group relative flex items-center justify-between gap-2 overflow-hidden rounded-lg border border-border-default bg-bg-base/40 px-2.5 py-2 transition-all duration-200 hover:-translate-y-0.5 hover:border-[color:var(--c)] hover:shadow-[0_8px_20px_-12px_var(--c)]"
            >
              {hasWeight && (
                <span aria-hidden className="absolute inset-y-0 left-0 opacity-[0.16] transition-opacity group-hover:opacity-30"
                  style={{ width: `${fillPct}%`, background: `linear-gradient(90deg, ${color}, transparent)` }} />
              )}
              <span className="relative flex min-w-0 items-center gap-1.5">
                <span className="h-1.5 w-1.5 shrink-0 rounded-full" style={{ backgroundColor: color }} />
                <span className="truncate font-mono text-[11px] font-bold text-fg">{display}</span>
              </span>
              {hasWeight && (
                <span className="relative shrink-0 font-mono text-[10px] font-bold tabular-nums" style={{ color }}>
                  %{w.toFixed(2)}
                </span>
              )}
            </Link>
          );
        })}
      </div>
    </div>
  );
}

export default function StockDetail() {
  const { symbol } = useParams();
  const historySym = symbol.endsWith('.IS') ? symbol : `${symbol}.IS`;
  const chartSymbol = symbol.endsWith('.IS') ? symbol.replace('.IS', '') : symbol;

  return (
    <AssetDetailPage
      assetCode={symbol}
      assetType="STOCK"
      chartAssetType="BIST"
      queryKeyPrefix="stock"
      fetchAsset={() => stockService.getByCode(symbol)}
      fetchHistory={(_, range) => stockService.getHistory(historySym, range)}
      backRoute="/stocks"
      excludeCompare={[historySym]}
      renderHeader={(asset) => <StockHeader asset={asset} />}
      renderMetadata={(asset) => <StockMetadata asset={asset} />}
      renderBelowChart={(asset) => <StockConstituents asset={asset} />}
      showBuyButton={true}
      getBuyProps={(asset) => ({
        assetType: 'STOCK',
        assetCode: asset.code || symbol,
        assetName: asset.name || chartSymbol,
        currentPrice: asset.price,
      })}
    />
  );
}
