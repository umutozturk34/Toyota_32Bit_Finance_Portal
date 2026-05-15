import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { forexService } from './services/forexService';
import { getBaseCurrency } from '../../shared/constants/forex';
import { useMoney } from '../../shared/hooks/useMoney';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

function ForexHeader({ asset, code }) {
  const base = getBaseCurrency(code);

  return (
    <>
      {asset.image
        ? (/^https?:\/\//i.test(asset.image)
            ? <img src={asset.image} alt={code} className="h-6 w-9 rounded-sm object-cover" />
            : <span className="text-2xl leading-none">{asset.image}</span>)
        : <span className="text-2xl">💱</span>}
      <div>
        <h1 className="text-xl font-bold text-fg">{base}/TRY</h1>
        <p className="text-xs text-fg-muted">{asset.name}</p>
      </div>
    </>
  );
}

function ForexMetadata({ asset }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const meta = asset.metadata || {};
  const sellingPrice = meta.sellingPrice;
  const buyingPrice = meta.buyingPrice;
  return (
    <MetadataTiles tiles={[
      { label: t('marketDetail.forex.sell'), value: money(sellingPrice ?? asset.price) },
      buyingPrice != null && { label: t('marketDetail.forex.buy'), value: money(buyingPrice) },
      meta.effectiveBuyingPrice != null && { label: t('marketDetail.forex.banknoteBuy'), value: money(meta.effectiveBuyingPrice) },
      meta.effectiveSellingPrice != null && { label: t('marketDetail.forex.banknoteSell'), value: money(meta.effectiveSellingPrice) },
    ]} />
  );
}

export default function ForexDetail() {
  const { code } = useParams();
  const sellingPriceGetter = (asset) => asset.metadata?.sellingPrice ?? asset.price;

  return (
    <AssetDetailPage
      assetCode={code}
      assetType="FOREX"
      chartAssetType="FOREX"
      queryKeyPrefix="forex"
      fetchAsset={() => forexService.getByCode(code)}
      fetchHistory={(_, range) => forexService.getHistory(code, range)}
      backRoute="/forex"
      renderHeader={(asset) => <ForexHeader asset={asset} code={code} />}
      renderMetadata={(asset) => <ForexMetadata asset={asset} />}
      getBuyProps={(asset) => ({
        assetType: 'FOREX',
        assetCode: code,
        assetName: asset.name,
        currentPrice: sellingPriceGetter(asset),
      })}
    />
  );
}
