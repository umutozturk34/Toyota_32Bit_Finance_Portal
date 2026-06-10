import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { viopService } from './services/viopService';
import { formatPrice } from '../../shared/utils/formatters';
import { useMoney } from '../../shared/hooks/useMoney';
import { viopQuoteCurrency } from '../../shared/utils/priceCurrency';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import MetadataTiles from '../../shared/components/asset/MetadataTiles';
import MarketOpenDerivativeModal from '../portfolio/components/MarketOpenDerivativeModal';

const fmt = (price) => (price != null ? formatPrice(price) : '—');

function formatExpiry(dateStr, localeTag) {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: 'numeric' });
}

function ViopHeader({ asset }) {
  const meta = asset.metadata || {};
  const subtitle = [meta.kind, meta.underlying].filter(Boolean).join(' · ');
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-indigo-400/10 text-indigo-400 text-xs font-bold">
        {meta.kind === 'OPTION' ? 'OPT' : 'FUT'}
      </span>
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{asset.code}</h1>
        {subtitle && <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{subtitle}</p>}
      </div>
    </>
  );
}

// Last price/change now live in the chart Data Window; this strip keeps only the contract specification the
// Data Window does not carry (underlying, expiry, size, margin, strike, etc.).
function ViopMetadata({ asset }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const meta = asset.metadata || {};
  const localeTag = t('common.localeTag');
  const isOption = meta.kind === 'OPTION';
  const currency = viopQuoteCurrency(asset.code);
  return (
    <MetadataTiles tiles={[
      { label: t('viop.underlying'), value: meta.underlying || '—' },
      { label: t('viop.expiry'), value: formatExpiry(meta.expiryDate, localeTag) },
      meta.contractSize != null && { label: t('viop.contractSize'), value: fmt(meta.contractSize) },
      meta.initialMargin != null && { label: t('viop.initialMargin'), value: money(meta.initialMargin, currency) },
      meta.settlementType && { label: t('viop.settlement'), value: meta.settlementType },
      isOption && meta.optionSide && { label: t('viop.optionSide'), value: meta.optionSide },
      isOption && meta.strikePrice != null && { label: t('viop.strike'), value: fmt(meta.strikePrice) },
      meta.volumeLot != null && { label: t('viop.volumeLot'), value: fmt(meta.volumeLot) },
    ]} />
  );
}

export default function ViopDetail() {
  const { symbol } = useParams();

  return (
    <AssetDetailPage
      assetCode={symbol}
      assetType="VIOP"
      chartAssetType="VIOP"
      queryKeyPrefix="viop"
      fetchAsset={() => viopService.getByCode(symbol)}
      fetchHistory={(_, range) => viopService.getHistory(symbol, range)}
      clientSideRangeFilter
      backRoute="/viop"
      excludeCompare={[symbol]}
      renderHeader={(asset) => <ViopHeader asset={asset} />}
      renderMetadata={(asset) => <ViopMetadata asset={asset} />}
      showBuyButton={true}
      buyModalComponent={MarketOpenDerivativeModal}
      getBuyProps={(asset) => (asset.price == null ? null : {
        assetCode: asset.code || symbol,
        assetName: asset.name || symbol,
        currentPrice: asset.price,
        metadata: asset.metadata,
      })}
    />
  );
}
