import { useParams } from 'react-router-dom';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { stockService } from './services/stockService';
import { getChangeClass, changeColors, formatPrice, formatVolume, formatPercentAbs } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';

const fmt = (price) => formatPrice(price, { locale: 'tr-TR' });

function StockHeader({ asset }) {
  const displaySymbol = asset.code?.replace('.IS', '') || '';
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 rounded-full bg-success/10 text-success text-sm font-bold">
        {displaySymbol.slice(0, 3).toUpperCase()}
      </span>
      <div>
        <h1 className="text-xl font-bold text-fg">{displaySymbol}</h1>
        <p className="text-xs text-fg-muted">{asset.name}</p>
      </div>
    </>
  );
}

function StockMetadata({ asset }) {
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);

  return (
    <>
      <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Fiyat</p>
          <p className="text-lg font-mono font-bold text-fg">₺{fmt(asset.price)}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Değişim</p>
          <div className={`flex items-center gap-1 text-lg font-mono font-bold ${changeColors[cls]}`}>
            {asset.changePercent > 0 ? <ChevronUp className="h-4 w-4" /> : asset.changePercent < 0 ? <ChevronDown className="h-4 w-4" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </div>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Hacim</p>
          <p className="text-lg font-mono font-bold text-fg">{formatVolume(meta.volume)}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Değişim (TL)</p>
          <p className={`text-lg font-mono font-bold ${changeColors[cls]}`}>₺{fmt(asset.changeAmount)}</p>
        </div>
      </motion.div>

      {(meta.openPrice != null || meta.dayHigh != null || meta.dayLow != null) && (
        <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-3 gap-4">
          {meta.openPrice != null && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="text-xs text-fg-muted">Açılış</span>
              <span className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.openPrice)}</span>
            </div>
          )}
          {meta.dayHigh != null && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="flex items-center gap-1 text-xs text-fg-muted"><ChevronUp className="h-3 w-3 text-success" />En Yüksek</span>
              <span className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.dayHigh)}</span>
            </div>
          )}
          {meta.dayLow != null && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="flex items-center gap-1 text-xs text-fg-muted"><ChevronDown className="h-3 w-3 text-danger" />En Düşük</span>
              <span className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.dayLow)}</span>
            </div>
          )}
        </motion.div>
      )}
    </>
  );
}

export default function StockDetail() {
  const { symbol } = useParams();
  const historySym = symbol.endsWith('.IS') ? symbol : `${symbol}.IS`;
  const chartSymbol = symbol.endsWith('.IS') ? symbol.replace('.IS', '') : symbol;
  const isIndexCheck = (asset) => asset?.metadata?.stockSegment && asset.metadata.stockSegment !== 'EQUITY';

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
      showBuyButton={true}
      getBuyProps={(asset) => {
        if (isIndexCheck(asset)) return null;
        return {
          assetType: 'STOCK',
          assetCode: asset.code || symbol,
          assetName: asset.name || chartSymbol,
          currentPrice: asset.price,
        };
      }}
    />
  );
}
