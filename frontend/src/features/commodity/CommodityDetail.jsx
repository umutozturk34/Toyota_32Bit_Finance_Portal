import { useParams } from 'react-router-dom';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { commodityService } from './services/commodityService';
import { getChangeClass, changeColors, formatPrice, formatPercentAbs } from '../../shared/utils/formatters';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

const fmt = (price) => formatPrice(price, { locale: 'tr-TR' });

function CommodityHeader({ asset }) {
  const meta = asset.metadata || {};
  const display = asset.name || asset.code;
  const subtitle = [asset.code, meta.unit].filter(Boolean).join(' · ');
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 rounded-full bg-orange-400/10 text-orange-400 text-sm font-bold">
        {(asset.code || '').slice(0, 3).toUpperCase()}
      </span>
      <div>
        <h1 className="text-xl font-bold text-fg">{display}</h1>
        {subtitle && <p className="text-xs text-fg-muted">{subtitle}</p>}
      </div>
    </>
  );
}

function CommodityMetadata({ asset }) {
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);
  const usd = meta.currentPriceUsd;
  return (
    <MetadataTiles tiles={[
      { label: 'Fiyat (TRY)', value: `₺${fmt(asset.price)}` },
      { label: 'Fiyat (USD)', value: usd != null ? `$${fmt(usd)}` : '—' },
      {
        label: '24s Δ',
        color: changeColors[cls],
        value: (
          <span className="flex items-center gap-0.5">
            {asset.changePercent > 0 ? <ChevronUp className="h-3 w-3" /> : asset.changePercent < 0 ? <ChevronDown className="h-3 w-3" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </span>
        ),
      },
      { label: 'Δ (TL)', value: `₺${fmt(asset.changeAmount)}`, color: changeColors[cls] },
      meta.sellingPrice != null && { label: 'Alım', value: `₺${fmt(meta.sellingPrice)}` },
      meta.openPrice != null && { label: 'Açılış', value: `₺${fmt(meta.openPrice)}` },
      meta.dayHigh != null && { label: 'En Yüksek', value: `₺${fmt(meta.dayHigh)}`, color: 'text-success' },
      meta.dayLow != null && { label: 'En Düşük', value: `₺${fmt(meta.dayLow)}`, color: 'text-danger' },
      meta.volume != null && meta.volume > 0 && { label: 'Hacim', value: meta.volume.toLocaleString('tr-TR') },
    ]} />
  );
}

export default function CommodityDetail() {
  const { code } = useParams();

  return (
    <AssetDetailPage
      assetCode={code}
      assetType="COMMODITY"
      chartAssetType="COMMODITY"
      queryKeyPrefix="commodity"
      fetchAsset={() => commodityService.getByCode(code)}
      fetchHistory={(_, range) => commodityService.getHistory(code, range)}
      backRoute="/commodities"
      excludeCompare={[code]}
      renderHeader={(asset) => <CommodityHeader asset={asset} />}
      renderMetadata={(asset) => <CommodityMetadata asset={asset} />}
      showBuyButton={true}
      getBuyProps={(asset) => ({
        assetType: 'COMMODITY',
        assetCode: asset.code || code,
        assetName: asset.name || asset.code || code,
        currentPrice: asset.price,
      })}
    />
  );
}
