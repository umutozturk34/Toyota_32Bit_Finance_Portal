import { useParams } from 'react-router-dom';
import { LineChart, Users as UsersIcon, Activity } from 'lucide-react';
import { fundService } from './fundService';
import { formatPriceTRY, formatVolume, formatCompactTRY } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';

function FundHeader({ asset }) {
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 rounded-full bg-violet-400/10 text-violet-400">
        <LineChart className="h-4 w-4" />
      </span>
      <div>
        <h1 className="text-xl font-bold text-fg">{asset.code}</h1>
        <p className="text-xs text-fg-muted">{asset.name || asset.code}</p>
      </div>
    </>
  );
}

function FundMetadata({ asset }) {
  const meta = asset.metadata || {};

  return (
    <>
      <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Fiyat</p>
          <p className="text-lg font-mono font-bold text-fg">{formatPriceTRY(asset.price)}</p>
        </div>
        {meta.fundType === 'BYF' && meta.bulletinPrice != null && (
          <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
            <p className="text-xs text-fg-muted mb-1">Borsa Fiyatı</p>
            <p className="text-lg font-mono font-bold text-fg">{formatPriceTRY(meta.bulletinPrice)}</p>
          </div>
        )}
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Fon Türü</p>
          <span className="inline-block rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-xs font-medium uppercase tracking-wider text-accent-bright">
            {meta.fundType || 'FON'}
          </span>
        </div>
        <div className="rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <p className="text-xs text-fg-muted mb-1">Portföy Büyüklüğü</p>
          <p className="text-lg font-mono font-bold text-fg">{formatCompactTRY(meta.portfolioSize)}</p>
        </div>
      </motion.div>

      <motion.div variants={cardVariants} initial="hidden" animate="show" className="grid grid-cols-2 gap-4">
        {meta.fundType === 'YAT' && meta.investorCount != null && (
          <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
            <span className="flex items-center gap-2 text-xs text-fg-muted"><UsersIcon className="h-3.5 w-3.5" />Yatırımcı Sayısı</span>
            <span className="text-sm font-mono font-semibold text-fg">{formatVolume(meta.investorCount)}</span>
          </div>
        )}
        <div className="flex items-center justify-between rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover">
          <span className="flex items-center gap-2 text-xs text-fg-muted"><Activity className="h-3.5 w-3.5" />Pay Sayısı</span>
          <span className="text-sm font-mono font-semibold text-fg">{formatVolume(meta.shareCount)}</span>
        </div>
      </motion.div>
    </>
  );
}

export default function FundDetail() {
  const { code } = useParams();

  return (
    <AssetDetailPage
      assetCode={code}
      assetType="FUND"
      chartAssetType="FUND"
      queryKeyPrefix="fund"
      fetchAsset={() => fundService.getByCode(code)}
      fetchHistory={(_, range) => fundService.getHistory(code, range)}
      backRoute="/funds"
      renderHeader={(asset) => <FundHeader asset={asset} />}
      renderMetadata={(asset) => <FundMetadata asset={asset} />}
      getBuyProps={(asset) => ({
        assetType: 'FUND',
        assetCode: asset.code || code,
        assetName: asset.name || code,
        currentPrice: asset.price,
      })}
    />
  );
}
