import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { stockService } from './services/stockService';
import { getChangeClass, changeColors, formatVolume, formatPercentAbs } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

function StockHeader({ asset }) {
  const displaySymbol = asset.code?.replace('.IS', '') || '';
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-success/10 text-success text-sm font-bold">
        {displaySymbol.slice(0, 3).toUpperCase()}
      </span>
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{displaySymbol}</h1>
        <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{asset.name}</p>
      </div>
    </>
  );
}

function StockMetadata({ asset }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changePercent);
  const priceNum = Number(asset.price);
  const changeNum = Number(asset.changeAmount);
  const derivedClose = Number.isFinite(priceNum) && Number.isFinite(changeNum) ? priceNum - changeNum : null;
  const closeValue = meta.previousClose ?? derivedClose;
  return (
    <MetadataTiles tiles={[
      { label: t('marketDetail.priceLabel'), value: money(asset.price) },
      {
        label: t('marketDetail.changeLabel'),
        color: changeColors[cls],
        value: (
          <span className="flex items-center gap-0.5">
            {asset.changePercent > 0 ? <ChevronUp className="h-3 w-3" /> : asset.changePercent < 0 ? <ChevronDown className="h-3 w-3" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </span>
        ),
      },
      { label: t('marketDetail.changeAmountTRY'), value: money(asset.changeAmount), color: changeColors[cls] },
      meta.openPrice != null && { label: t('market.stock.openLabel'), value: money(meta.openPrice) },
      meta.dayHigh != null && { label: t('market.stock.highLabel'), value: money(meta.dayHigh), color: 'text-success' },
      meta.dayLow != null && { label: t('market.stock.lowLabel'), value: money(meta.dayLow), color: 'text-danger' },
      closeValue != null && { label: t('market.stock.closeLabel'), value: money(closeValue) },
      { label: t('market.stock.volumeLabel'), value: formatVolume(meta.volume) },
    ]} />
  );
}

export default function StockDetail() {
  const { symbol } = useParams();
  const historySym = symbol.endsWith('.IS') ? symbol : `${symbol}.IS`;
  const chartSymbol = symbol.endsWith('.IS') ? symbol.replace('.IS', '') : symbol;
  const isIndexCheck = (asset) => asset?.metadata?.stockSegment && asset.metadata.stockSegment !== 'EQUITY';

  return (
    <AssetDetailPage
      assetCode={symbol}
      assetType="STOCK"
      chartAssetType="BIST"
      queryKeyPrefix="stock"
      fetchAsset={() => stockService.getByCode(symbol)}
      fetchHistory={(_, range) => stockService.getHistory(historySym, range)}
      backRoute="/stocks"
      excludeCompare={[historySym]}
      renderHeader={(asset) => <StockHeader asset={asset} />}
      renderMetadata={(asset) => <StockMetadata asset={asset} />}
      showBuyButton={true}
      getBuyProps={(asset) => {
        if (isIndexCheck(asset)) return null;
        return {
          assetType: 'STOCK',
          assetCode: asset.code || symbol,
          assetName: asset.name || chartSymbol,
          currentPrice: asset.price,
        };
      }}
    />
  );
}
