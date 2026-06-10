import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { cryptoService } from './services/cryptoService';
import { formatCompactNumber } from '../../shared/utils/formatters';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

function CryptoHeader({ asset }) {
  const meta = asset.metadata || {};
  return (
    <>
      {asset.image ? (
        <img src={asset.image} alt={meta.symbol} className="w-8 h-8 shrink-0 rounded-full" />
      ) : (
        <span className="flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-warning/10 text-warning text-sm font-bold">
          {(meta.symbol || asset.code || '').slice(0, 2).toUpperCase()}
        </span>
      )}
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{meta.symbol || asset.code}</h1>
        <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{asset.name}</p>
      </div>
    </>
  );
}

// Price/change/volume now live in the chart Data Window; this strip keeps only the market-level figures the
// Data Window does not carry (market cap + 24h traded volume).
function CryptoMetadata({ asset }) {
  const { t } = useTranslation();
  const meta = asset.metadata || {};
  return (
    <MetadataTiles tiles={[
      { label: t('market.crypto.marketCapLabel'), value: formatCompactNumber(meta.marketCap, 'USD') },
      { label: t('marketDetail.crypto.volume24h'), value: formatCompactNumber(meta.totalVolume) },
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
