import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { forexService } from './services/forexService';
import { getBaseCurrency } from '../../shared/constants/forex';
import { forexName } from '../../shared/utils/commodityName';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';

function ForexHeader({ asset, code }) {
  const { t } = useTranslation();
  const base = getBaseCurrency(code);

  return (
    <>
      {asset.image
        ? (/^https?:\/\//i.test(asset.image)
            ? <img src={asset.image} alt={code} className="h-6 w-9 shrink-0 rounded-sm object-cover" />
            : <span className="text-2xl leading-none shrink-0">{asset.image}</span>)
        : <span className="text-2xl shrink-0">💱</span>}
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{base}/TRY</h1>
        <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{forexName(t, code, asset.name)}</p>
      </div>
    </>
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
      getBuyProps={(asset) => ({
        assetType: 'FOREX',
        assetCode: code,
        assetName: asset.name,
        currentPrice: sellingPriceGetter(asset),
      })}
    />
  );
}
