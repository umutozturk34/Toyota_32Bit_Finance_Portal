import { useParams } from 'react-router-dom';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { forexService } from './services/forexService';
import { getForexFlag, getBaseCurrency } from '../../shared/constants/forex';
import { getChangeClass, changeColors, formatPrice, formatChange, formatPercent } from '../../shared/utils/formatters';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

const fmt = (price) => formatPrice(price, { locale: 'tr-TR', minDecimals: 4, maxDecimals: 4 });

function ForexHeader({ asset, code }) {
  const flag = getForexFlag(code);
  const base = getBaseCurrency(code);

  return (
    <>
      <span className="text-2xl">{flag}</span>
      <div>
        <h1 className="text-xl font-bold text-fg">{base}/TRY</h1>
        <p className="text-xs text-fg-muted">{asset.name}</p>
      </div>
    </>
  );
}

function ForexMetadata({ asset }) {
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changeAmount);
  const sellingPrice = meta.sellingPrice;
  return (
    <MetadataTiles tiles={[
      { label: 'Alış', value: `₺${fmt(sellingPrice ?? asset.price)}` },
      { label: 'Satış', value: `₺${fmt(asset.price)}` },
      {
        label: '24s Δ',
        color: changeColors[cls],
        value: (
          <span className="flex items-center gap-0.5">
            {asset.changeAmount > 0 ? <ArrowUpRight className="h-3 w-3" /> : asset.changeAmount < 0 ? <ArrowDownRight className="h-3 w-3" /> : null}
            {formatPercent(asset.changePercent)}
          </span>
        ),
      },
      { label: 'Δ (TL)', value: `${formatChange(asset.changeAmount)} TRY`, color: changeColors[cls] },
      meta.forexBuying != null && { label: 'TCMB Alış', value: `₺${fmt(meta.forexBuying)}` },
      meta.forexSelling != null && { label: 'TCMB Satış', value: `₺${fmt(meta.forexSelling)}` },
      meta.banknoteBuying != null && { label: 'Efektif Alış', value: `₺${fmt(meta.banknoteBuying)}` },
      meta.banknoteSelling != null && { label: 'Efektif Satış', value: `₺${fmt(meta.banknoteSelling)}` },
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
