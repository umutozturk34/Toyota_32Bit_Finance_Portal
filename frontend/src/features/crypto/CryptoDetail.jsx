import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { cryptoService } from './services/cryptoService';
import { getChangeClass, changeColors, formatCompactNumber, formatPercentAbs, formatPriceUSD, formatPriceTRY } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
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

function CryptoMetadata({ asset }) {
  const { t } = useTranslation();
  const { formatCompact } = useMoney();
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);
  return (
    <MetadataTiles tiles={[
      { label: t('marketDetail.crypto.priceUsd'), value: formatPriceUSD(meta.currentPriceUsd) },
      { label: t('marketDetail.crypto.priceTry'), value: formatPriceTRY(asset.price) },
      {
        label: t('marketDetail.crypto.change24h'),
        color: changeColors[cls],
        value: (
          <span className="flex items-center gap-0.5">
            {asset.changePercent > 0 ? <ArrowUpRight className="h-3 w-3" /> : asset.changePercent < 0 ? <ArrowDownRight className="h-3 w-3" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </span>
        ),
      },
      { label: t('marketDetail.crypto.changeAmountUsd'), value: formatPriceUSD(asset.changeAmount), color: changeColors[cls] },
      { label: t('market.crypto.marketCapLabel'), value: formatCompact(meta.marketCap, 'USD') },
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
