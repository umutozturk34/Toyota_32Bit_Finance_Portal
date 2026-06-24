import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LineChart, ExternalLink, ScrollText, Clock, CalendarClock, RefreshCw } from 'lucide-react';
import { fundService } from './services/fundService';
import { formatVolume } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import RiskMeter from '../../shared/components/asset/RiskMeter';
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

// Fund classification strip — now individuated mini-cards (not one merged tile-grid) so the settlement valörs
// and trade window stand out as their own accent-highlighted cards. Price/change live in the chart Data Window;
// risk lives in the FON KÜNYESİ RiskMeter below, so it is intentionally NOT repeated here.
function FundMetadata({ asset }) {
  const { t } = useTranslation();
  const { formatCompact: moneyCompact } = useMoney();
  const meta = asset.metadata || {};
  const tiles = [
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
    meta.sellValor != null && { label: t('marketDetail.fund.sellValor'), value: `T+${meta.sellValor}`, highlight: true, Icon: CalendarClock },
    meta.buybackValor != null && { label: t('marketDetail.fund.buybackValor'), value: `T+${meta.buybackValor}`, highlight: true, Icon: RefreshCw },
    (meta.tradeStartTime && meta.tradeEndTime) && {
      label: t('marketDetail.fund.tradeHours'),
      value: `${meta.tradeStartTime}–${meta.tradeEndTime}`,
      highlight: true,
      Icon: Clock,
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
  ].filter(Boolean);

  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      {tiles.map((tile) => (
        <div
          key={tile.label}
          className={`flex flex-col gap-1 rounded-xl border bg-bg-elevated/50 p-3 transition-colors hover:bg-bg-elevated/70 ${
            tile.highlight ? 'border-border-default border-l-2 border-l-accent' : 'border-border-default'
          }`}
        >
          <div className="flex items-center gap-1.5">
            {tile.Icon && <tile.Icon className={`h-3 w-3 shrink-0 ${tile.highlight ? 'text-accent' : 'text-fg-subtle'}`} />}
            <p className="truncate text-[10px] uppercase tracking-wider text-fg-subtle">{tile.label}</p>
          </div>
          <div className="truncate text-sm font-semibold text-fg" title={typeof tile.value === 'string' ? tile.value : undefined}>
            {tile.value}
          </div>
        </div>
      ))}
    </div>
  );
}

// The fund's profile (risk, allocation, returns) rendered as a colourful section BELOW the chart — same place
// the BIST index constituents sit. The price chart stays the primary view; this expands under it and the
// related news follows. Allocation leads (the donut is the visual anchor); risk + returns sit beside it and
// stack underneath on mobile.
function FundProfileSection({ asset }) {
  const { t } = useTranslation();
  const meta = asset?.metadata || {};
  const allocations = meta.allocations || [];
  const hasReturns = ['return1m', 'return3m', 'return6m', 'returnYtd', 'return1y', 'return3y', 'return5y']
    .some((k) => meta[k] != null);
  const hasAnything = meta.riskValue != null || allocations.length > 0 || hasReturns;
  if (!hasAnything) return null;

  return (
    <section className="space-y-3" data-tour="fund-profile">
      <div className="flex items-center gap-2.5">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-accent/15 text-accent">
          <ScrollText className="h-4 w-4" />
        </span>
        <h2 className="text-sm font-bold uppercase tracking-wider text-fg">{t('marketDetail.fund.sectionTitle')}</h2>
        <span className="h-px flex-1 bg-gradient-to-r from-border-default to-transparent" />
      </div>
      <div className="grid gap-3 lg:grid-cols-3">
        {allocations.length > 0 && (
          <div className="lg:col-span-1">
            <AllocationPie allocations={allocations} />
          </div>
        )}
        <div className={`space-y-3 ${allocations.length > 0 ? 'lg:col-span-2' : 'lg:col-span-3'}`}>
          {meta.riskValue != null && <RiskMeter value={meta.riskValue} />}
          {hasReturns && <ReturnsList metadata={meta} />}
        </div>
      </div>
    </section>
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
      // The rich profile (risk · allocation · returns) is the important bit, so it sits directly under the
      // chart; the detailed classification field-cards follow below it, then the news.
      renderMetadata={(asset) => <FundProfileSection asset={asset} />}
      renderBelowChart={(asset) => <FundMetadata asset={asset} />}
      getBuyProps={(asset) => ({
        assetType: 'FUND',
        assetCode: asset.code || code,
        assetName: asset.name || code,
        currentPrice: asset.price,
      })}
    />
  );
}
