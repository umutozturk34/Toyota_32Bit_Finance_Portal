import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, GitCompare, ChevronDown, LineChart } from 'lucide-react';
import { ShoppingCart } from '../feedback/AnimatedIcons';
import useChartRange from '../../hooks/useChartRange';
import useNavigationBack from '../../hooks/useNavigationBack';
import Button from '../buttons/Button';
import IconButton from '../buttons/IconButton';
import { Skeleton, SkeletonChart, SkeletonStat } from '../feedback/Skeleton';
import ErrorState from '../feedback/ErrorState';
import MarketAddPositionModal from '../../../features/portfolio/components/MarketAddPositionModal';
import LightweightChart from '../../../features/chart/components/LightweightChart';
import AssetActionsBar from '../../../features/watch/components/AssetActionsBar';
import MarketStatusBadge from '../layout/MarketStatusBadge';
import AssetRelatedNews from '../../../features/news/components/AssetRelatedNews';
import { transformCandles, transformFundCandles } from '../../utils/candleTransform';
import { useRateHistory } from '../../hooks/useRateHistory';
import { priceCurrencyOf, viopQuoteCurrency } from '../../utils/priceCurrency';

function extractCurrentPrice(asset) {
  if (!asset) return null;
  return asset.priceTry ?? asset.currentPriceTry ?? asset.price ?? asset.currentPrice ?? null;
}

const TRANSFORM_MAP = {
  FUND: transformFundCandles,
};

const CANDLE_MONEY_FIELDS = [
  'open', 'high', 'low', 'close',
  'sellingPrice', 'buyingPrice', 'effectiveBuyingPrice', 'effectiveSellingPrice', 'bulletinPrice',
];

function naturalCurrencyFor(assetType, asset, assetCode) {
  if (assetType === 'CRYPTO') {
    if ((assetCode || '').toLowerCase() === 'tether') return 'TRY';
    return 'USD';
  }
  if (assetType === 'VIOP') return viopQuoteCurrency(assetCode);
  // Commodities are cross-converted to TRY at ingest, so candles are always TRY.
  if (assetType === 'COMMODITY') return 'TRY';
  return 'TRY';
}

const FOREX_PRICE_FIELDS = new Set(['sellingPrice', 'buyingPrice', 'effectiveBuyingPrice', 'effectiveSellingPrice']);

function convertCandleSet(data, convertAt, baseCurrency, naturalCurrency) {
  if (!data?.candles) return data;
  return {
    ...data,
    candles: data.candles.map((candle) => {
      const date = candle.candleDate || candle.date;
      const next = { ...candle };
      for (const field of CANDLE_MONEY_FIELDS) {
        if (next[field] != null) {
          const rateField = FOREX_PRICE_FIELDS.has(field) ? field : undefined;
          next[field] = convertAt(next[field], baseCurrency, date, naturalCurrency, rateField);
        }
      }
      return next;
    }),
  };
}

const RANGE_DAYS = { '1W': 7, '1M': 31, '3M': 93, '6M': 186, '1Y': 372, '3Y': 1098, '5Y': 1830 };
const CLIENT_FILTER_RANGES = new Set(['1W', '1M', '3M', '6M', '1Y', '3Y']);

function filterCandlesClientSide(candles, range) {
  if (!Array.isArray(candles) || candles.length === 0) return candles;
  if (range === 'ALL' || !RANGE_DAYS[range]) return candles;
  const cutoffMs = Date.now() - RANGE_DAYS[range] * 24 * 60 * 60 * 1000;
  return candles.filter((c) => {
    const ts = c.candleDate || c.date;
    if (!ts) return false;
    return new Date(ts).getTime() >= cutoffMs;
  });
}

export default function AssetDetailPage({
  assetCode,
  assetType,
  chartAssetType,
  fetchAsset,
  fetchHistory,
  queryKeyPrefix,
  errorMessage,
  notFoundMessage,
  backRoute,
  renderHeader,
  renderMetadata,
  renderBelowChart,
  getBuyProps,
  showBuyButton = true,
  buyModalComponent: BuyModalComponent = MarketAddPositionModal,
  clientSideRangeFilter = false,
  collapsibleChart = false,
  dataTour,
}) {
  const { t } = useTranslation();
  const { convertAt, resolveTarget } = useRateHistory();
  const goBack = useNavigationBack(backRoute);
  const navigate = useNavigate();
  const resolvedError = errorMessage ?? t('marketDetail.error');
  const resolvedNotFound = notFoundMessage ?? t('marketDetail.notFound');
  const [buyOpen, setBuyOpen] = useState(false);
  const [showSecondaryLines, setShowSecondaryLines] = useState(true);
  // The user's open/closed choice for THIS asset; null means "use the per-asset default". Reset on every asset
  // switch below so each detail page starts from its own default instead of inheriting the previous one.
  const [chartOpenOverride, setChartOpenOverride] = useState(null);
  const [chartOverrideFor, setChartOverrideFor] = useState(assetCode);
  if (chartOverrideFor !== assetCode) {
    // Navigated to a different asset within the same mounted page (route param changed, no remount) — drop the
    // previous asset's manual toggle so the new asset's default applies. Set-state-during-render is the React-
    // endorsed way to reset state on a prop change (re-renders immediately, before paint).
    setChartOverrideFor(assetCode);
    setChartOpenOverride(null);
  }
  const [timeRange, setTimeRange] = useChartRange();
  const effectiveRange = clientSideRangeFilter && !CLIENT_FILTER_RANGES.has(timeRange) ? '1Y' : timeRange;

  const { data: asset, isLoading, isFetching, error, refetch: refetchAsset } = useQuery({
    queryKey: [queryKeyPrefix, assetCode],
    queryFn: () => fetchAsset(assetCode),
  });

  // Whether this asset's chart is collapsible + collapsed by default. A predicate form lets the caller decide
  // from the LOADED asset (e.g. a stock is an index when its segment isn't EQUITY) so detection never depends on
  // a hand-kept code list. The chart shows open unless the user toggled it (override) for this asset.
  const collapsibleChartActive = typeof collapsibleChart === 'function'
    ? Boolean(collapsibleChart(asset))
    : Boolean(collapsibleChart);
  const chartOpen = chartOpenOverride !== null ? chartOpenOverride : !collapsibleChartActive;

  const fetchRange = clientSideRangeFilter ? '5Y' : timeRange;
  const { data: historyRaw } = useQuery({
    queryKey: [`${queryKeyPrefix}History`, assetCode, fetchRange],
    queryFn: () => fetchHistory(assetCode, fetchRange),
    placeholderData: (prev) => prev,
  });
  const filteredHistoryRaw = useMemo(
    () => (clientSideRangeFilter ? filterCandlesClientSide(historyRaw, effectiveRange) : historyRaw),
    [historyRaw, effectiveRange, clientSideRangeFilter],
  );

  const transform = TRANSFORM_MAP[assetType] || transformCandles;
  const naturalCurrency = naturalCurrencyFor(assetType, asset, assetCode);
  const baseCurrency = naturalCurrency;
  const transformedData = useMemo(() => transform(filteredHistoryRaw), [transform, filteredHistoryRaw]);
  const convertTarget = useMemo(() => resolveTarget(baseCurrency, naturalCurrency), [resolveTarget, baseCurrency, naturalCurrency]);
  const chartData = useMemo(() => {
    // No-op currency case (display === natural, e.g. TRY for BIST/forex): skip the per-candle FX map entirely. It
    // would only spread thousands of identical candles, and — crucially — returning the SAME array reference means
    // the chart is NOT re-rendered/re-setData a second time when the FX rate history loads afterwards. Real
    // (cross-currency) conversions still run as before.
    if (!convertTarget || convertTarget === baseCurrency) return transformedData;
    return convertCandleSet(transformedData, convertAt, baseCurrency, naturalCurrency);
  }, [transformedData, convertTarget, baseCurrency, naturalCurrency, convertAt]);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton w="3.5rem" h="3.5rem" circle />
          <div className="space-y-2">
            <Skeleton w="9rem" h="1.6rem" className="rounded-lg" />
            <Skeleton w="6rem" h="0.85rem" />
          </div>
          <Skeleton w="8rem" h="2.5rem" className="ml-auto hidden rounded-xl sm:block" />
        </div>
        <SkeletonChart h="22rem" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <SkeletonStat />
          <SkeletonStat />
          <SkeletonStat />
          <SkeletonStat />
        </div>
      </div>
    );
  }
  if (error || !asset) {
    return (
      <ErrorState
        message={error ? resolvedError : resolvedNotFound}
        onRetry={refetchAsset}
        retryLoading={isFetching}
      />
    );
  }

  const buyProps = getBuyProps ? getBuyProps(asset) : null;

  return (
    <div className="space-y-6" data-tour={dataTour}>
      <motion.div
        initial={{ opacity: 0, y: -12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between gap-2 flex-wrap"
      >
        <div className="flex items-center gap-3 min-w-0 flex-wrap">
          <IconButton
            variant="secondary"
            size={9}
            shape="square"
            icon={<ArrowLeft className="h-4 w-4" />}
            aria-label={t('common.back', { defaultValue: 'back' })}
            onClick={goBack}
            className="hover:-translate-y-0.5"
          />
          {renderHeader(asset)}
          <MarketStatusBadge market={(assetType || '').toUpperCase()} />
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {asset && (
            <AssetActionsBar
              marketType={assetType}
              assetCode={assetCode}
              currentPrice={extractCurrentPrice(asset)}
              currency={priceCurrencyOf({ type: assetType, ...asset })}
            />
          )}
          {asset && (
            <button
              type="button"
              onClick={() => {
                const next = new URLSearchParams({
                  tab: 'compare',
                  codes: assetCode,
                  types: assetType,
                  range: '1Y',
                  from: 'asset',
                  fromType: assetType,
                  fromCode: assetCode,
                });
                navigate(`/analytics?${next.toString()}`);
              }}
              className="inline-flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-3 py-2 min-h-10 text-xs font-semibold text-fg-muted hover:text-accent hover:border-accent/40 hover:bg-accent/10 transition-colors cursor-pointer"
              title={t('marketDetail.compareAction', { defaultValue: 'Karşılaştır' })}
            >
              <GitCompare className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">{t('marketDetail.compareAction', { defaultValue: 'Karşılaştır' })}</span>
            </button>
          )}
          {showBuyButton && buyProps && (
            <Button
              variant="primary"
              size="md"
              leftIcon={<ShoppingCart className="h-4 w-4" />}
              onClick={() => setBuyOpen(true)}
              className="rounded-xl bg-success hover:bg-success/90"
            >
              {t('marketDetail.addToPortfolio')}
            </Button>
          )}
        </div>
      </motion.div>

      {collapsibleChartActive && (
        <button
          type="button"
          onClick={() => setChartOpenOverride(!chartOpen)}
          className="flex w-full items-center gap-2.5 rounded-2xl border border-border-default bg-bg-elevated/50 px-4 py-3 text-left backdrop-blur transition-colors hover:bg-surface/40 cursor-pointer"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-accent/15 text-accent">
            <LineChart className="h-4 w-4" />
          </span>
          <span className="text-sm font-bold uppercase tracking-wider text-fg">{t('marketDetail.priceChart')}</span>
          <ChevronDown className={`ml-auto h-4 w-4 shrink-0 text-fg-subtle transition-transform duration-200 ${chartOpen ? 'rotate-180' : ''}`} />
        </button>
      )}

      {/* The chart expands/collapses with a height glide instead of snapping — the chart's own ResizeObserver
          re-measures as the container grows, so the canvas sizes correctly. initial={false} keeps a normal
          (always-open) detail page's chart static on first load; only a toggle on an index page animates. */}
      <AnimatePresence initial={false}>
        {chartOpen && (
          <motion.div
            key="detail-chart"
            data-tour="detail-chart"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.32, ease: [0.22, 1, 0.36, 1] }}
            style={{ overflow: 'hidden' }}
          >
            <LightweightChart
              data={chartData}
              symbol={assetCode}
              assetType={chartAssetType || assetType}
              timeRange={effectiveRange}
              onTimeRangeChange={setTimeRange}
              showSecondaryLines={showSecondaryLines}
              onToggleSecondaryLines={() => setShowSecondaryLines((v) => !v)}
            />
          </motion.div>
        )}
      </AnimatePresence>

      {/* Everything that isn't the price chart sits BELOW it — the asset's property strip first, then any
          asset-specific supporting block (fund profile, index constituents), then the news. This order is the
          same on every detail page so the chart is always the primary, top-of-page view. */}
      {renderMetadata && <div data-tour="detail-metadata">{renderMetadata(asset)}</div>}

      {renderBelowChart && renderBelowChart(asset)}

      <AssetRelatedNews
        assetCode={assetCode}
        assetName={asset?.name ?? asset?.assetName ?? asset?.shortName ?? asset?.longName ?? asset?.title ?? null}
        assetType={assetType}
      />

      {buyOpen && buyProps && (
        <BuyModalComponent
          {...buyProps}
          onClose={() => setBuyOpen(false)}
          onComplete={() => setBuyOpen(false)}
        />
      )}
    </div>
  );
}
