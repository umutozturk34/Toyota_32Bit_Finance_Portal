import { useParams } from 'react-router-dom';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { cryptoService } from './services/cryptoService';
import { getChangeClass, changeColors, formatPriceUSD, formatPriceTRY, formatCompactNumber, formatPercentAbs } from '../../shared/utils/formatters';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

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
    <MetadataTiles tiles={[
      { label: 'Fiyat (USD)', value: formatPriceUSD(meta.currentPriceUsd) },
      { label: 'Fiyat (TRY)', value: formatPriceTRY(asset.price) },
      {
        label: '24s Değişim',
        color: changeColors[cls],
        value: (
          <span className="flex items-center gap-0.5">
            {asset.changePercent > 0 ? <ArrowUpRight className="h-3 w-3" /> : asset.changePercent < 0 ? <ArrowDownRight className="h-3 w-3" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </span>
        ),
      },
      { label: '24s Δ (USD)', value: formatPriceUSD(asset.changeAmount), color: changeColors[cls] },
      { label: 'Piyasa Değeri', value: `$${formatCompactNumber(meta.marketCap)}` },
      { label: 'Hacim (24s)', value: formatCompactNumber(meta.totalVolume) },
    ]} />
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
