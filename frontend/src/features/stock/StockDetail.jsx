import { useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Briefcase, CalendarDays, MapPin, Landmark, Award, Layers, Search } from 'lucide-react';
import { stockService } from './services/stockService';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import { assetRoute } from '../watch/lib/watchConstants';
import { assetColorStyle } from '../../shared/utils/assetColor';
import { indexFriendlyName, isSizeIndex } from '../../shared/utils/bistIndexNames';

function StockHeader({ asset }) {
  const displaySymbol = asset.code?.replace('.IS', '') || '';
  // For an index row the ticker (e.g. XU100) is opaque, so the title carries the friendly name (BIST 100)
  // and the raw code drops to the subtitle. A normal stock keeps ticker title + company-name subtitle.
  const friendly = indexFriendlyName(displaySymbol);
  const isIndex = friendly !== displaySymbol;
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
        <h1 className="text-xl font-bold text-fg truncate">{isIndex ? friendly : displaySymbol}</h1>
        <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{isIndex ? displaySymbol : asset.name}</p>
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

  // Short, single-line facts share a row; the HQ address (city) is a long string that truncated unreadably in a
  // narrow column, so it gets its own full-width wrapping row at the bottom — like the sector sentence.
  const shortStats = [
    foundedYear && { label: t('marketDetail.stock.founded'), value: foundedYear, Icon: CalendarDays, color: '#a78bfa' },
    meta.exchange && { label: t('marketDetail.stock.exchange'), value: meta.exchange, Icon: Landmark, color: '#38bdf8' },
  ].filter(Boolean);

  const hasKunye = Boolean(meta.sector) || shortStats.length > 0 || Boolean(meta.city);
  if (!hasKunye && !indices.length) return null;

  // The size/benchmark indices (BIST 30/50/100) signal the stock's stature, so they lead as prominent tier
  // pills; the sector indices follow as colour-keyed chips. Each carries the index weight.
  const sizeIndices = indices.filter((ix) => isSizeIndex(ix.indexCode));
  const sectorIndices = indices.filter((ix) => !isSizeIndex(ix.indexCode));

  return (
    <div className="space-y-3">
      {hasKunye && (
        <div className="space-y-3 rounded-2xl border border-border-default bg-bg-elevated/50 p-4 backdrop-blur">
          {meta.sector && (
            <KunyeStat Icon={Briefcase} color="#818cf8" label={t('marketDetail.stock.sector')} value={meta.sector} wrap />
          )}
          {shortStats.length > 0 && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {shortStats.map((s) => (
                <KunyeStat key={s.label} Icon={s.Icon} color={s.color} label={s.label} value={s.value} />
              ))}
            </div>
          )}
          {meta.city && (
            <KunyeStat Icon={MapPin} color="#2dd4bf" label={t('marketDetail.stock.city')} value={meta.city} wrap />
          )}
        </div>
      )}
      {indices.length > 0 && (
        <div className="rounded-2xl border border-border-default bg-bg-elevated/40 p-3.5 backdrop-blur">
          <p className="mb-2.5 text-[10px] font-bold uppercase tracking-wider text-fg-subtle">
            {t('marketDetail.stock.indices')}
          </p>
          {sizeIndices.length > 0 && (
            <div className="mb-2.5 flex flex-wrap gap-2">
              {sizeIndices.map((ix) => (
                <Link
                  key={ix.indexCode}
                  to={assetRoute('STOCK', `${ix.indexCode}.IS`)}
                  // Leader-board gold — a FIXED #fbbf24 (same in dark + light) so it never flips to a muddy bronze;
                  // only the medal icon + weight % carry the gold, the label stays neutral so it's always readable.
                  style={{ backgroundColor: 'rgba(251,191,36,0.12)', borderColor: 'rgba(251,191,36,0.40)' }}
                  className="group inline-flex items-center gap-2 rounded-lg border px-2.5 py-1.5 transition-shadow duration-200 hover:shadow-[0_8px_20px_-12px_rgba(251,191,36,0.9)]"
                  title={indexFriendlyName(ix.indexCode)}
                >
                  <Award className="h-3.5 w-3.5 shrink-0" style={{ color: '#fbbf24' }} />
                  <span className="text-xs font-bold text-fg">{indexFriendlyName(ix.indexCode)}</span>
                  {ix.weight != null && (
                    <span className="font-mono text-[11px] font-bold tabular-nums" style={{ color: '#f59e0b' }}>%{Number(ix.weight).toFixed(2)}</span>
                  )}
                </Link>
              ))}
            </div>
          )}
          {sectorIndices.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {sectorIndices.map((ix) => {
                const name = indexFriendlyName(ix.indexCode);
                return (
                  <Link
                    key={ix.indexCode}
                    to={assetRoute('STOCK', `${ix.indexCode}.IS`)}
                    className="group inline-flex max-w-full items-center gap-1.5 rounded-md border border-border-default bg-bg-base/40 px-2 py-1 text-fg-muted transition-colors duration-200 hover:border-accent/40 hover:text-fg"
                    title={name}
                  >
                    <Layers className="h-3 w-3 shrink-0 text-fg-subtle group-hover:text-accent" />
                    <span className="truncate text-[11px] font-semibold">{name}</span>
                    {ix.weight != null && (
                      <span className="font-mono text-[10px] font-bold tabular-nums text-fg-subtle">%{Number(ix.weight).toFixed(2)}</span>
                    )}
                  </Link>
                );
              })}
            </div>
          )}
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
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: `color-mix(in srgb, ${color} 14%, transparent)`, color }}>
        <Icon className="h-4 w-4" />
      </span>
      <div className="min-w-0">
        <p className="text-[10px] uppercase tracking-wider text-fg-subtle">{label}</p>
        <p className={`mt-0.5 text-sm font-semibold text-fg ${wrap ? 'leading-snug' : 'truncate'}`}>{value}</p>
      </div>
    </div>
  );
}

// Leader-board MEDAL tiers by weight RANK so the eye instantly sorts the index: top 3 gold, ranks 4–7 silver,
// the rest indigo. (rank is 0-based; weightless members fall through to the neutral wash, no tier.)
function tierColor(rank) {
  if (rank < 3) return '#fbbf24'; // gold — 🥇 podium
  if (rank < 7) return '#94a3b8'; // silver — 🥈 chasers
  return '#818cf8'; // indigo — the pack (bronze alt offered; indigo stays calm for the long tail)
}

// Reverse view: when the asset is an index, the stocks that make it up — each a chip linking to its own
// detail. Sits below the chart. Empty (renders nothing) for a normal stock or an index whose constituents have
// not been enriched yet.
function StockConstituents({ asset }) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const constituents = useMemo(() => asset.metadata?.constituents || [], [asset.metadata?.constituents]);
  // Heaviest holdings lead; sector-only members (null weight) sink to the end. The fill bars are normalised to
  // the top holding so the panel reads as a weight heatmap at a glance.
  const sorted = useMemo(
    () => [...constituents].sort((a, b) => (Number(b.weight) || -1) - (Number(a.weight) || -1)),
    [constituents],
  );
  const maxWeight = useMemo(() => Math.max(...sorted.map((c) => Number(c.weight) || 0), 0.01), [sorted]);
  // Weight rank keyed by symbol off the FULL sorted order, so the tier colour is stable while search filters the grid.
  const rankBySymbol = useMemo(() => new Map(sorted.map((c, i) => [c.stockSymbol, i])), [sorted]);
  const needle = query.trim().toUpperCase();
  const filtered = needle
    ? sorted.filter((c) => (c.stockSymbol || '').toUpperCase().includes(needle))
    : sorted;
  // A 50+ stock index is hard to scan, so the panel becomes searchable past a dozen holdings.
  const searchable = constituents.length > 12;

  if (!constituents.length) return null;

  return (
    <div className="overflow-hidden rounded-2xl border border-border-default bg-bg-elevated/50 p-3.5 backdrop-blur">
      <div className="mb-3 flex flex-wrap items-center gap-2.5">
        <span className="h-2 w-2 rounded-full bg-accent" style={{ boxShadow: '0 0 10px var(--color-accent-glow)' }} />
        <p className="text-[11px] font-bold uppercase tracking-wider text-fg">{t('marketDetail.stock.constituents')}</p>
        <span className="rounded-full bg-surface/70 px-2 py-0.5 text-[10px] font-bold tabular-nums text-fg-muted">{constituents.length}</span>
        <span className="ml-1 hidden h-px flex-1 bg-gradient-to-r from-border-default to-transparent sm:block" />
        {searchable && (
          <div className="relative ml-auto w-full sm:w-44">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-fg-subtle" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('marketDetail.stock.constituentSearch')}
              className="w-full rounded-lg border border-border-default bg-bg-base/60 py-1.5 pl-8 pr-2.5 text-[11px] font-medium text-fg placeholder:text-fg-subtle focus:border-accent/50 focus:outline-none"
            />
          </div>
        )}
      </div>
      {/* Leader-board ranking: the weight-fill bar + % are coloured by RANK TIER — green podium (top 3), gold
          chasers (4–7), indigo pack (rest) — so the eye sorts the index instantly. Within a tier the bar length
          and opacity still ramp with the actual weight. Weightless members get a faint neutral wash. */}
      {filtered.length === 0 ? (
        <p className="py-6 text-center text-xs text-fg-muted">{t('marketDetail.stock.constituentNoMatch')}</p>
      ) : (
        <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {filtered.map((c) => {
            const display = (c.stockSymbol || '').replace('.IS', '');
            const w = Number(c.weight);
            const hasWeight = c.weight != null && !Number.isNaN(w);
            const ratio = hasWeight ? w / maxWeight : 0;
            const percep = Math.sqrt(ratio);
            const fillPct = hasWeight ? Math.max(8, percep * 100) : 0;
            const intensity = (24 + percep * 42).toFixed(0); // 24%→66% within the tier, by weight
            const color = tierColor(rankBySymbol.get(c.stockSymbol) ?? 99);
            return (
              <Link
                key={c.stockSymbol}
                to={assetRoute('STOCK', c.stockSymbol)}
                style={{ '--c': color }}
                className="group relative flex items-center justify-between gap-2 overflow-hidden rounded-lg border border-border-default bg-bg-base/30 px-2.5 py-2 transition-colors duration-200 hover:border-[color:var(--c)] hover:bg-bg-base/50"
              >
                {hasWeight ? (
                  <span aria-hidden className="absolute inset-y-0 left-0"
                    style={{ width: `${fillPct}%`, background: `linear-gradient(90deg, color-mix(in srgb, ${color} ${intensity}%, transparent), transparent)` }} />
                ) : (
                  <span aria-hidden className="absolute inset-0" style={{ background: 'color-mix(in srgb, var(--color-accent) 7%, transparent)' }} />
                )}
                <span className="relative truncate font-mono text-[11px] font-bold text-fg">{display}</span>
                {hasWeight && (
                  <span className="relative shrink-0 font-mono text-[10px] font-bold tabular-nums" style={{ color }}>
                    %{w.toFixed(2)}
                  </span>
                )}
              </Link>
            );
          })}
        </div>
      )}
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
      // An index (main or sector/sub) opens with its chart collapsed — its constituents are the real content and
      // many indices have only a point or two of price history. Driven by the loaded asset's segment (not a code
      // list), so EVERY index is covered; an ordinary EQUITY stock keeps its chart open.
      collapsibleChart={(asset) => {
        const segment = asset?.metadata?.stockSegment;
        return Boolean(segment) && segment !== 'EQUITY';
      }}
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
