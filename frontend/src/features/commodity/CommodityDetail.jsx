import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { commodityService } from './services/commodityService';
import { getChangeClass, changeColors, formatPercentAbs } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
import { commodityName } from '../../shared/utils/commodityName';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

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
  const { format: money } = useMoney();
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);
  const usd = meta.currentPriceUsd;
  const localeTag = t('common.localeTag');
  return (
    <MetadataTiles tiles={[
      { label: t('marketDetail.commodity.priceTry'), value: money(asset.price) },
      { label: t('marketDetail.commodity.priceUsd'), value: usd != null ? money(usd, 'USD') : '—' },
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
      { label: t('marketDetail.forex.deltaTL'), value: money(asset.changeAmount), color: changeColors[cls] },
      meta.sellingPrice != null && { label: t('marketDetail.commodity.buy'), value: money(meta.sellingPrice) },
      meta.openPrice != null && { label: t('market.stock.openLabel'), value: money(meta.openPrice) },
      meta.dayHigh != null && { label: t('market.stock.highLabel'), value: money(meta.dayHigh), color: 'text-success' },
      meta.dayLow != null && { label: t('market.stock.lowLabel'), value: money(meta.dayLow), color: 'text-danger' },
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
