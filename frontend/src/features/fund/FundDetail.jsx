import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LineChart, ExternalLink, PieChart, ChevronRight } from 'lucide-react';
import { fundService } from './services/fundService';
import { formatVolume } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';
import BaseModal from '../../shared/components/modal/BaseModal';
import RiskBadge from '../../shared/components/asset/RiskBadge';
import AllocationPie from './components/AllocationPie';
import ReturnsList from './components/ReturnsList';

function FundHeader({ asset }) {
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-violet-400/10 text-violet-400">
        <LineChart className="h-4 w-4" />
      </span>
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{asset.code}</h1>
        <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{asset.name || asset.code}</p>
      </div>
    </>
  );
}

// Price/change/bulletin now live in the chart Data Window; this strip keeps only the fund classification the
// Data Window does not carry (type, category, rank, market share, portfolio, investor, ISIN, KAP).
function FundMetadata({ asset }) {
  const { t } = useTranslation();
  const { formatCompact: moneyCompact } = useMoney();
  const meta = asset.metadata || {};
  return (
    <div className="space-y-3">
      <MetadataTiles tiles={[
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
        meta.riskValue != null && {
          label: t('marketDetail.fund.riskLabel'),
          value: <RiskBadge value={meta.riskValue} size="sm" />,
        },
        meta.sellValor != null && { label: t('marketDetail.fund.sellValor'), value: `T+${meta.sellValor}` },
        meta.buybackValor != null && { label: t('marketDetail.fund.buybackValor'), value: `T+${meta.buybackValor}` },
        (meta.tradeStartTime && meta.tradeEndTime) && {
          label: t('marketDetail.fund.tradeHours'),
          value: `${meta.tradeStartTime}–${meta.tradeEndTime}`,
        },
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

// Sits under the Data Window in the chart's right column: a single trigger that opens the fund's risk,
// allocation and returns in a modal — keeps the right column short instead of a long scroll.
function FundDetailCard({ asset }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const meta = asset?.metadata || {};
  const allocations = meta.allocations || [];
  return (
    <>
      <button
        type="button"
        data-tour="fund-detail-card"
        onClick={() => setOpen(true)}
        className="w-full flex items-center justify-between gap-2 rounded-xl border border-border-default bg-surface/40 px-3 py-2.5 text-sm font-medium text-fg hover:border-accent/40 hover:bg-accent/5 transition-colors cursor-pointer"
      >
        <span className="inline-flex items-center gap-2">
          <PieChart className="h-4 w-4 text-accent" />
          {t('marketDetail.fund.viewDetail')}
        </span>
        <ChevronRight className="h-4 w-4 text-fg-muted shrink-0" />
      </button>
      {/* Hidden close target so the onboarding tour can dismiss the modal after the allocation step. */}
      <button
        type="button"
        data-tour-close="fund-detail"
        aria-hidden="true"
        tabIndex={-1}
        onClick={() => setOpen(false)}
        style={{ position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap', border: 0 }}
      />
      <BaseModal
        isOpen={open}
        onClose={() => setOpen(false)}
        icon={LineChart}
        title={asset.code}
        subtitle={t('marketDetail.fund.detailTitle')}
        size="lg"
      >
        <div className="space-y-3 overflow-x-hidden">
          {meta.riskValue != null && (
            <div className="rounded-xl border border-border-default bg-surface/40 p-3 flex items-center justify-between">
              <span className="text-xs font-medium text-fg-muted">{t('marketDetail.fund.riskLabel')}</span>
              <RiskBadge value={meta.riskValue} size="lg" />
            </div>
          )}
          <AllocationPie allocations={allocations} />
          <ReturnsList metadata={meta} />
        </div>
      </BaseModal>
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
      renderSidebar={(asset) => <FundDetailCard asset={asset} />}
      getBuyProps={(asset) => ({
        assetType: 'FUND',
        assetCode: asset.code || code,
        assetName: asset.name || code,
        currentPrice: asset.price,
      })}
    />
  );
}
