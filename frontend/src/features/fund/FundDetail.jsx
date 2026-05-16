import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LineChart, ExternalLink } from 'lucide-react';
import { fundService } from './services/fundService';
import { formatVolume } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';
import RiskBadge from '../../shared/components/asset/RiskBadge';
import AllocationPie from './components/AllocationPie';
import ReturnsList from './components/ReturnsList';

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
  const { t } = useTranslation();
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const meta = asset.metadata || {};
  return (
    <div className="space-y-3">
      <MetadataTiles tiles={[
        { label: t('marketDetail.priceLabel'), value: money(asset.price) },
        meta.fundType === 'BYF' && meta.bulletinPrice != null && { label: t('marketDetail.fund.exchange'), value: money(meta.bulletinPrice) },
        {
          label: t('marketDetail.fund.fundType'),
          value: (
            <span className="inline-block rounded-md border border-accent/20 bg-accent/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-accent-bright">
              {meta.fundType ? t(`market.fund.shortType.${meta.fundType}`, { defaultValue: meta.fundType }) : t('market.fund.fallbackBadge')}
            </span>
          ),
        },
        (meta.category || meta.subCategory) && {
          label: t('marketDetail.fund.categoryLabel'),
          value: t(`fundCategory.${meta.category || meta.subCategory}`, { defaultValue: meta.category || meta.subCategory }),
        },
        meta.categoryRank != null && meta.categoryTotalFunds != null && {
          label: t('market.fund.rankLabel'),
          value: `${meta.categoryRank}/${meta.categoryTotalFunds}`,
        },
        meta.marketShare != null && { label: t('marketDetail.fund.marketShareLabel'), value: `%${Number(meta.marketShare).toFixed(2)}` },
        { label: t('market.fund.portfolioLabel'), value: moneyCompact(meta.portfolioSize) },
        meta.fundType === 'YAT' && meta.investorCount != null && { label: t('market.fund.investorLabel'), value: formatVolume(meta.investorCount) },
        meta.shareCount != null && { label: t('market.fund.shareCountLabel'), value: formatVolume(meta.shareCount) },
        meta.isinCode && { label: 'ISIN', value: <span className="font-mono text-[11px]">{meta.isinCode}</span> },
        meta.kapLink && {
          label: 'KAP',
          value: (
            <a href={meta.kapLink} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-accent hover:underline">
              <ExternalLink className="h-3 w-3" /> {t('marketDetail.fund.kapLinkLabel')}
            </a>
          ),
        },
      ]} />
    </div>
  );
}

function FundSidebar({ asset }) {
  const { t } = useTranslation();
  const meta = asset?.metadata || {};
  const allocations = meta.allocations || [];
  return (
    <>
      {meta.riskValue != null && (
        <div className="rounded-xl border border-border-default bg-surface/40 p-3 flex items-center justify-between">
          <span className="text-xs font-medium text-fg-muted">{t('marketDetail.fund.riskLabel')}</span>
          <RiskBadge value={meta.riskValue} size="lg" />
        </div>
      )}
      <AllocationPie allocations={allocations} />
      <ReturnsList metadata={meta} />
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
      renderSidebar={(asset) => <FundSidebar asset={asset} />}
      getBuyProps={(asset) => ({
        assetType: 'FUND',
        assetCode: asset.code || code,
        assetName: asset.name || code,
        currentPrice: asset.price,
      })}
    />
  );
}
