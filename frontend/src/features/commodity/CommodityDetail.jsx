import { useParams } from 'react-router-dom';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { commodityService } from './services/commodityService';
import { getChangeClass, changeColors, formatPrice, formatPercentAbs } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';

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
    <>
      <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Fiyat (TRY)</p>
          <p className="text-lg font-mono font-bold text-fg">₺{fmt(asset.price)}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Fiyat (USD)</p>
          <p className="text-lg font-mono font-bold text-fg">{usd != null ? `$${fmt(usd)}` : '—'}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">24s Değişim</p>
          <div className={`flex items-center gap-1 text-lg font-mono font-bold ${changeColors[cls]}`}>
            {asset.changePercent > 0 ? <ChevronUp className="h-4 w-4" /> : asset.changePercent < 0 ? <ChevronDown className="h-4 w-4" /> : null}
            {formatPercentAbs(asset.changePercent)}
          </div>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Değişim (TL)</p>
          <p className={`text-lg font-mono font-bold ${changeColors[cls]}`}>₺{fmt(asset.changeAmount)}</p>
        </div>
      </motion.div>

      {(meta.openPrice != null || meta.dayHigh != null || meta.dayLow != null || meta.sellingPrice != null) && (
        <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {meta.sellingPrice != null && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="text-xs text-fg-muted">Alım Fiyatı</span>
              <span className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.sellingPrice)}</span>
            </div>
          )}
          {meta.openPrice != null && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="text-xs text-fg-muted">Açılış</span>
              <span className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.openPrice)}</span>
            </div>
          )}
          {meta.dayHigh != null && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="flex items-center gap-1 text-xs text-fg-muted"><ChevronUp className="h-3 w-3 text-success" />En Yüksek</span>
              <span className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.dayHigh)}</span>
            </div>
          )}
          {meta.dayLow != null && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="flex items-center gap-1 text-xs text-fg-muted"><ChevronDown className="h-3 w-3 text-danger" />En Düşük</span>
              <span className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.dayLow)}</span>
            </div>
          )}
          {meta.volume != null && meta.volume > 0 && (
            <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
              <span className="text-xs text-fg-muted">Hacim</span>
              <span className="text-sm font-mono font-semibold text-fg">{meta.volume.toLocaleString('tr-TR')}</span>
            </div>
          )}
        </motion.div>
      )}
    </>
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
