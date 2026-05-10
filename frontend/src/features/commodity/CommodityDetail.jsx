import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { commodityService } from './services/commodityService';
import { getChangeClass, changeColors, formatPrice, formatPercentAbs } from '../../shared/utils/formatters';
import { commodityName } from '../../shared/utils/commodityName';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

const fmt = (price) => formatPrice(price);

function CommodityHeader({ asset }) {
  const { t } = useTranslation();
  const meta = asset.metadata || {};
  const display = commodityName(t, asset.code, asset.name);
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
  const { t } = useTranslation();
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);
  const usd = meta.currentPriceUsd;
  const localeTag = t('common.localeTag');
  return (
    <MetadataTiles tiles={[
      { label: t('marketDetail.commodity.priceTry'), value: `₺${fmt(asset.price)}` },
      { label: t('marketDetail.commodity.priceUsd'), value: usd != null ? `$${fmt(usd)}` : '—' },
      {
        label: t('marketDetail.forex.delta24h'),
        color: changeColors[cls],
        value: (
          <span className="flex items-center gap-0.5">
            {asset.changePercent > 0 ? <ChevronUp className="h-3 w-3" /> : asset.changePercent < 0 ? <ChevronDown className="h-3 w-3" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </span>
        ),
      },
      { label: t('marketDetail.forex.deltaTL'), value: `₺${fmt(asset.changeAmount)}`, color: changeColors[cls] },
      meta.sellingPrice != null && { label: t('marketDetail.commodity.buy'), value: `₺${fmt(meta.sellingPrice)}` },
      meta.openPrice != null && { label: t('market.stock.openLabel'), value: `₺${fmt(meta.openPrice)}` },
      meta.dayHigh != null && { label: t('market.stock.highLabel'), value: `₺${fmt(meta.dayHigh)}`, color: 'text-success' },
      meta.dayLow != null && { label: t('market.stock.lowLabel'), value: `₺${fmt(meta.dayLow)}`, color: 'text-danger' },
      meta.volume != null && meta.volume > 0 && { label: t('market.stock.volumeLabel'), value: meta.volume.toLocaleString(localeTag) },
    ]} />
  );
}

export default function CommodityDetail() {
  const { code } = useParams();
  const { t } = useTranslation();

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
        assetName: commodityName(t, asset.code, asset.name) || code,
        currentPrice: asset.price,
      })}
    />
  );
}
