import { useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/AnimatedIcons';
import { forexService } from './forexService';
import { getForexFlag, getBaseCurrency } from '../../shared/constants/forex';
import { getChangeClass, changeColors, formatPrice, formatChange, formatPercent } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import AssetDetailPage from '../../shared/components/AssetDetailPage';

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

function ForexMetadata({ asset, code }) {
  const meta = asset.metadata || {};
  const cls = getChangeClass(asset.changeAmount);
  const sellingPrice = meta.sellingPrice;

  return (
    <>
      <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Alış Fiyatı</p>
          <p className="text-lg font-mono font-bold text-fg">₺{fmt(sellingPrice ?? asset.price)}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Satış Fiyatı</p>
          <p className="text-lg font-mono font-bold text-fg">₺{fmt(asset.price)}</p>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">24s Değişim</p>
          <div className={`flex items-center gap-1 text-lg font-mono font-bold ${changeColors[cls]}`}>
            {asset.changeAmount > 0 ? <ArrowUpRight className="h-4 w-4" /> : asset.changeAmount < 0 ? <ArrowDownRight className="h-4 w-4" /> : null}
            {formatPercent(asset.changePercent)}
          </div>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Değişim (TL)</p>
          <p className={`text-lg font-mono font-bold ${changeColors[cls]}`}>{formatChange(asset.changeAmount)} TRY</p>
        </div>
      </motion.div>

      {(meta.forexBuying || meta.forexSelling || meta.banknoteBuying || meta.banknoteSelling) && (
        <motion.div variants={cardVariants} initial="hidden" animate="show" className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs font-semibold text-fg-muted mb-3">TCMB Kurları</p>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {meta.forexBuying != null && (
              <div>
                <p className="text-[11px] text-fg-muted mb-1">Döviz Alış</p>
                <p className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.forexBuying)}</p>
              </div>
            )}
            {meta.forexSelling != null && (
              <div>
                <p className="text-[11px] text-fg-muted mb-1">Döviz Satış</p>
                <p className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.forexSelling)}</p>
              </div>
            )}
            {meta.banknoteBuying != null && (
              <div>
                <p className="text-[11px] text-fg-muted mb-1">Efektif Alış</p>
                <p className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.banknoteBuying)}</p>
              </div>
            )}
            {meta.banknoteSelling != null && (
              <div>
                <p className="text-[11px] text-fg-muted mb-1">Efektif Satış</p>
                <p className="text-sm font-mono font-semibold text-fg">₺{fmt(meta.banknoteSelling)}</p>
              </div>
            )}
          </div>
        </motion.div>
      )}
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
      fetchAsset={() => forexService.getForexByCode(code)}
      fetchHistory={(_, range) => forexService.getForexHistory(code, range)}
      backRoute="/forex"
      renderHeader={(asset) => <ForexHeader asset={asset} code={code} />}
      renderMetadata={(asset) => <ForexMetadata asset={asset} code={code} />}
      getBuyProps={(asset) => ({
        assetType: 'FOREX',
        assetCode: code,
        assetName: asset.name,
        currentPrice: sellingPriceGetter(asset),
      })}
    />
  );
}
