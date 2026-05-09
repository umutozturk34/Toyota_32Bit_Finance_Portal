import { useParams } from 'react-router-dom';
import { LineChart } from 'lucide-react';
import { fundService } from './services/fundService';
import { formatPriceTRY, formatVolume, formatCompactTRY } from '../../shared/utils/formatters';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';

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
    <MetadataTiles tiles={[
      { label: 'Fiyat', value: formatPriceTRY(asset.price) },
      meta.fundType === 'BYF' && meta.bulletinPrice != null && { label: 'Borsa', value: formatPriceTRY(meta.bulletinPrice) },
      {
        label: 'Fon Türü',
        value: (
          <span className="inline-block rounded-md border border-accent/20 bg-accent/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-accent-bright">
            {meta.fundType || 'FON'}
          </span>
        ),
      },
      { label: 'Portföy', value: formatCompactTRY(meta.portfolioSize) },
      meta.fundType === 'YAT' && meta.investorCount != null && { label: 'Yatırımcı', value: formatVolume(meta.investorCount) },
      meta.shareCount != null && { label: 'Pay Sayısı', value: formatVolume(meta.shareCount) },
    ]} />
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
