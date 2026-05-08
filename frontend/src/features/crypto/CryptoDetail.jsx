import { useParams } from 'react-router-dom';
import { Activity, BarChart2 } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { cryptoService } from './cryptoService';
import { getChangeClass, changeColors, formatPriceUSD, formatPriceTRY, formatCompactNumber, formatPercentAbs } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';

function CryptoHeader({ asset }) {
  const meta = asset.metadata || {};
  return (
    <>
      {asset.image ? (
        <img src={asset.image} alt={meta.symbol} className="w-8 h-8 rounded-full" />
      ) : (
        <span className="flex items-center justify-center w-8 h-8 rounded-full bg-warning/10 text-warning text-sm font-bold">
          {(meta.symbol || asset.code || '').slice(0, 2).toUpperCase()}
        </span>
      )}
      <div>
        <h1 className="text-xl font-bold text-fg">{meta.symbol || asset.code}</h1>
        <p className="text-xs text-fg-muted">{asset.name}</p>
      </div>
    </>
  );
}

function CryptoMetadata({ asset }) {
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);

  return (
    <>
      <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Fiyat (USD)</p>
          <p className="text-lg font-mono font-bold text-fg">{formatPriceUSD(meta.currentPriceUsd)}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Fiyat (TRY)</p>
          <p className="text-lg font-mono font-bold text-fg">{formatPriceTRY(asset.price)}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">24s Değişim</p>
          <div className={`flex items-center gap-1 text-lg font-mono font-bold ${changeColors[cls]}`}>
            {asset.changePercent > 0 ? <ArrowUpRight className="h-4 w-4" /> : asset.changePercent < 0 ? <ArrowDownRight className="h-4 w-4" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </div>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Piyasa Değeri</p>
          <p className="text-lg font-mono font-bold text-fg">${formatCompactNumber(meta.marketCap)}</p>
        </div>
      </motion.div>

      <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 gap-4">
        <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <span className="flex items-center gap-2 text-xs text-fg-muted"><ArrowDownRight className="h-3.5 w-3.5" />24s Değişim (USD)</span>
          <span className="text-sm font-mono font-semibold text-fg">{formatPriceUSD(asset.changeAmount)}</span>
        </div>
        <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <span className="flex items-center gap-2 text-xs text-fg-muted"><BarChart2 className="h-3.5 w-3.5" />Hacim (24s)</span>
          <span className="text-sm font-mono font-semibold text-fg">{formatCompactNumber(meta.totalVolume)}</span>
        </div>
      </motion.div>
    </>
  );
}

export default function CryptoDetail() {
  const { id } = useParams();

  return (
    <AssetDetailPage
      assetCode={id}
      assetType="CRYPTO"
      chartAssetType="CRYPTO"
      queryKeyPrefix="crypto"
      fetchAsset={cryptoService.getByCode}
      fetchHistory={cryptoService.getHistory}
      backRoute="/crypto"
      renderHeader={(asset) => <CryptoHeader asset={asset} />}
      renderMetadata={(asset) => <CryptoMetadata asset={asset} />}
      getBuyProps={(asset) => ({
        assetType: 'CRYPTO',
        assetCode: id,
        assetName: `${asset.metadata?.symbol} - ${asset.name}`,
        currentPrice: asset.price,
      })}
    />
  );
}
