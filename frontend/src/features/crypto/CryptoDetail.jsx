import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { cryptoService } from './services/cryptoService';
import { getChangeClass, changeColors, formatCompactNumber, formatPercentAbs } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
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
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);
  return (
    <MetadataTiles tiles={[
      { label: t('marketDetail.crypto.priceUsd'), value: money(meta.currentPriceUsd, 'USD') },
      { label: t('marketDetail.crypto.priceTry'), value: money(asset.price) },
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
      { label: t('marketDetail.crypto.changeAmountUsd'), value: money(asset.changeAmount, 'USD'), color: changeColors[cls] },
      { label: t('market.crypto.marketCapLabel'), value: `$${formatCompactNumber(meta.marketCap)}` },
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
