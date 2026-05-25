import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Layers, TrendingUp, TrendingDown } from 'lucide-react';
import { STALE } from '../../shared/constants/query';
import { viopService } from './services/viopService';
import { formatPrice } from '../../shared/utils/formatters';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/asset/ChangePercentBadge';
import FilterTabs from '../../shared/components/form/FilterTabs';
import MarketOpenDerivativeModal from '../portfolio/components/MarketOpenDerivativeModal';
import useListParams from '../../shared/hooks/useListParams';
import { useMoney } from '../../shared/hooks/useMoney';

const SORT_OPTION_IDS = ['changePercent', 'price', 'name'];

const UNDERLYING_CLASS_IDS = ['PAY', 'INDEX', 'CURRENCY', 'METAL'];

function classOf(categoryType) {
  if (categoryType.startsWith('PAY')) return 'PAY';
  if (categoryType.startsWith('INDEX')) return 'INDEX';
  if (categoryType.startsWith('CURRENCY')) return 'CURRENCY';
  return 'METAL';
}

function formatExpiry(dateStr, localeTag) {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: '2-digit' });
}

function buildViopName(meta, fallback, t, localeTag) {
  if (!meta || !meta.kind) return fallback;
  const parts = [];
  if (meta.underlying) parts.push(meta.underlying);
  if (meta.kind === 'OPTION') {
    if (meta.optionSide) parts.push(t(`viop.side.${meta.optionSide}`, { defaultValue: meta.optionSide }));
    if (meta.strikePrice != null) {
      const strike = Number(meta.strikePrice);
      parts.push(Number.isInteger(strike) ? String(strike) : strike.toFixed(2).replace(/\.?0+$/, ''));
    }
  } else if (meta.kind === 'FUTURE') {
    parts.push(t('viop.kind.FUTURE', { defaultValue: 'Vadeli' }));
  }
  let name = parts.join(' ');
  if (meta.expiryDate) name += ' · ' + formatExpiry(meta.expiryDate, localeTag);
  return name || fallback;
}

export default function ViopPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const listParams = useListParams();
  const { format: money } = useMoney();
  const kindFilter = listParams.filter || null;
  const classFilter = listParams.subFilter || null;

  const sortOptions = SORT_OPTION_IDS.map((id) => ({ id, label: t(`market.sort.${id}`, id) }));

  const { data: groupCounts = [] } = useQuery({
    queryKey: ['viopGroupCounts'],
    queryFn: viopService.getGroupCounts,
    staleTime: STALE.MEDIUM,
  });

  const futureCount = groupCounts
    .filter((g) => g.type.includes('FUTURE'))
    .reduce((sum, g) => sum + g.count, 0);
  const optionCount = groupCounts
    .filter((g) => g.type.includes('OPTION'))
    .reduce((sum, g) => sum + g.count, 0);

  const tabItems = [
    { type: 'FUTURE', count: futureCount, label: t('viop.kind.future') },
    { type: 'OPTION', count: optionCount, label: t('viop.kind.option') },
  ];

  const classCounts = UNDERLYING_CLASS_IDS
    .map((cls) => ({
      type: cls,
      label: t(`viop.class.${cls}`),
      count: groupCounts
        .filter((g) => classOf(g.type) === cls && (!kindFilter || g.type.includes(kindFilter)))
        .reduce((sum, g) => sum + g.count, 0),
    }))
    .filter((c) => c.count > 0);

  const queryParams = {
    ...listParams.params,
    ...(kindFilter && { segment: kindFilter }),
    ...(classFilter && { subType: classFilter }),
  };

  const renderCard = (contract, { setBuyTarget }) => {
    const meta = contract.metadata || {};
    const displayName = buildViopName(meta, contract.code, t, i18n.language);
    const isOption = meta.kind === 'OPTION';
    const currency = meta.currency || 'TRY';
    const tradeable = contract.price != null;
    const categoryLabel = meta.category
      ? t(`viop.category.${meta.category}`, meta.category)
      : meta.kind;
    return (
      <AssetCard
        key={contract.code}
        onClick={() => navigate(`/viop/${encodeURIComponent(contract.code)}`)}
        size="sm"
      >
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-sm font-semibold text-fg">{contract.code}</h3>
            {displayName && displayName !== contract.code && (
              <span className="block text-xs text-fg-muted leading-snug line-clamp-2 break-words">{displayName}</span>
            )}
          </div>
          {tradeable && (
            <div className="shrink-0">
              <AssetBuyButton
                title={t('viop.openPosition')}
                onClick={() => setBuyTarget({
                  assetCode: contract.code,
                  assetName: contract.name || contract.code,
                  price: contract.price,
                  metadata: meta,
                })}
              />
            </div>
          )}
        </div>

        <span className="mt-2 inline-flex w-fit max-w-full items-center truncate rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
          {categoryLabel}
        </span>

        <div className="mt-3">
          <p className="font-mono text-xl font-bold text-fg">
            {contract.price != null ? money(contract.price, currency) : t('viop.noPrice')}
          </p>
          {contract.changePercent != null && (
            <ChangePercentBadge
              value={contract.changePercent}
              positiveIcon={<TrendingUp className="h-3.5 w-3.5" />}
              negativeIcon={<TrendingDown className="h-3.5 w-3.5" />}
              size="sm"
              className="mt-1"
            />
          )}
        </div>

        <div className="mt-3 space-y-1 border-t border-border-default pt-3 text-xs">
          <div className="flex items-center justify-between gap-2">
            <span className="text-fg-muted shrink-0">{t('viop.underlying')}</span>
            <span className="truncate font-mono text-fg">{meta.underlying || '—'}</span>
          </div>
          <div className="flex items-center justify-between gap-2">
            <span className="text-fg-muted shrink-0">{t('viop.expiry')}</span>
            <span className="font-mono text-fg">{formatExpiry(meta.expiryDate, i18n.language)}</span>
          </div>
          {meta.initialMargin != null && (
            <div className="flex items-center justify-between gap-2">
              <span className="text-fg-muted shrink-0">{t('viop.initialMargin')}</span>
              <span className="font-mono text-fg">{money(meta.initialMargin, currency)}</span>
            </div>
          )}
          {isOption && meta.strikePrice != null && (
            <div className="flex items-center justify-between gap-2">
              <span className="text-fg-muted shrink-0">{t('viop.strike')} ({meta.optionSide})</span>
              <span className="font-mono text-fg">{formatPrice(meta.strikePrice)}</span>
            </div>
          )}
          {meta.volumeLot != null && (
            <div className="flex items-center justify-between gap-2">
              <span className="text-fg-muted shrink-0">{t('viop.volumeLot')}</span>
              <span className="font-mono text-fg">{formatPrice(meta.volumeLot)}</span>
            </div>
          )}
        </div>
      </AssetCard>
    );
  };

  return (
    <MarketListPage
      title={t('viop.title')}
      icon={<Layers className="h-5 w-5" />}
      emptyIcon={<Layers className="h-7 w-7 text-fg-subtle" />}
      marketType="VIOP"
      service={viopService}
      queryKey="viop"
      listParams={listParams}
      queryParams={queryParams}
      searchPlaceholder={t('viop.searchPlaceholder')}
      countLabel={t('viop.countLabel')}
      sortOptions={sortOptions}
      filterConfig={{
        tabItems,
        activeId: kindFilter ?? 'ALL',
        onSelect: (id) => listParams.update({ filter: id === 'ALL' ? '' : id, sub: '', page: 0 }),
        layoutId: 'viop-kind',
      }}
      preGridChildren={classCounts.length > 0 && (
        <FilterTabs
          items={classCounts}
          activeId={classFilter ?? 'ALL'}
          onSelect={(id) => listParams.setSubFilter(id === 'ALL' ? '' : id)}
          layoutId="viop-class"
        />
      )}
      renderCard={renderCard}
      buyModalComponent={MarketOpenDerivativeModal}
      loadingMessage={t('viop.loading')}
      errorMessage={t('viop.error')}
      emptyMessage={t('viop.empty')}
    />
  );
}
