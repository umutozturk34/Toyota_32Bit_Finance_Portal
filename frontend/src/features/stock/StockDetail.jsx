import { useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { stockService } from './services/stockService';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';
import { assetRoute } from '../watch/lib/watchConstants';
import { assetColorStyle } from '../../shared/utils/assetColor';

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

  const tiles = [
    meta.sector && { label: t('marketDetail.stock.sector'), value: meta.sector },
    foundedYear && { label: t('marketDetail.stock.founded'), value: foundedYear },
    meta.city && { label: t('marketDetail.stock.city'), value: meta.city },
    meta.exchange && { label: t('marketDetail.stock.exchange'), value: meta.exchange },
  ].filter(Boolean);

  if (!tiles.length && !indices.length) return null;

  return (
    <div className="space-y-3">
      <MetadataTiles tiles={tiles} />
      {indices.length > 0 && (
        <div>
          <p className="mb-1.5 text-[10px] uppercase tracking-wider text-fg-subtle">
            {t('marketDetail.stock.indices')}
          </p>
          <div className="flex flex-wrap gap-1.5">
            {indices.map((ix) => (
              <Link
                key={ix.indexCode}
                to={assetRoute('STOCK', `${ix.indexCode}.IS`)}
                className="inline-flex items-center gap-1.5 rounded-md border border-accent/20 bg-accent/10 px-2 py-1 text-[11px] font-semibold text-accent-bright transition-colors hover:border-accent/50 hover:bg-accent/15"
              >
                <span className="font-mono">{ix.indexCode}</span>
                {ix.weight != null && (
                  <span className="font-normal text-fg-muted">%{Number(ix.weight).toFixed(2)}</span>
                )}
              </Link>
            ))}
          </div>
        </div>
      )}
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
  return (
    <div className="rounded-xl border border-border-default bg-surface/40 p-3">
      <p className="mb-2 text-[10px] uppercase tracking-wider text-fg-subtle">
        {t('marketDetail.stock.constituents')} · {constituents.length}
      </p>
      <div className="flex flex-wrap gap-1.5">
        {constituents.map((c) => {
          const display = (c.stockSymbol || '').replace('.IS', '');
          return (
            <Link
              key={c.stockSymbol}
              to={assetRoute('STOCK', c.stockSymbol)}
              className="inline-flex items-center gap-1.5 rounded-md border border-border-default bg-bg-elevated px-2 py-1 text-[11px] font-semibold text-fg transition-colors hover:border-accent/50 hover:bg-accent/10"
            >
              <span className="font-mono">{display}</span>
              {c.weight != null && (
                <span className="font-normal text-fg-muted">%{Number(c.weight).toFixed(2)}</span>
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
      renderSidebar={(asset) => <StockConstituents asset={asset} />}
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
