import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, GitCompare } from 'lucide-react';
import { ShoppingCart } from '../feedback/AnimatedIcons';
import useChartRange from '../../hooks/useChartRange';
import useNavigationBack from '../../hooks/useNavigationBack';
import Button from '../buttons/Button';
import IconButton from '../buttons/IconButton';
import LoadingState from '../feedback/LoadingState';
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
  loadingMessage,
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
  dataTour,
}) {
  const { t } = useTranslation();
  const { convertAt, resolveTarget } = useRateHistory();
  const goBack = useNavigationBack(backRoute);
  const navigate = useNavigate();
  const resolvedLoading = loadingMessage ?? t('marketDetail.loading');
  const resolvedError = errorMessage ?? t('marketDetail.error');
  const resolvedNotFound = notFoundMessage ?? t('marketDetail.notFound');
  const [buyOpen, setBuyOpen] = useState(false);
  const [showSecondaryLines, setShowSecondaryLines] = useState(true);
  const [timeRange, setTimeRange] = useChartRange();
  const effectiveRange = clientSideRangeFilter && !CLIENT_FILTER_RANGES.has(timeRange) ? '1Y' : timeRange;

  const { data: asset, isLoading, isFetching, error, refetch: refetchAsset } = useQuery({
    queryKey: [queryKeyPrefix, assetCode],
    queryFn: () => fetchAsset(assetCode),
  });

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

  if (isLoading) return <LoadingState message={resolvedLoading} />;
  if (error || !asset) {
    return (
      <ErrorState
        message={error ? resolvedError : resolvedNotFound}
        onRetry={error ? refetchAsset : goBack}
        retryLoading={error ? isFetching : false}
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

      <div data-tour="detail-chart">
        <LightweightChart
          data={chartData}
          symbol={assetCode}
          assetType={chartAssetType || assetType}
          timeRange={effectiveRange}
          onTimeRangeChange={setTimeRange}
          showSecondaryLines={showSecondaryLines}
          onToggleSecondaryLines={() => setShowSecondaryLines((v) => !v)}
        />
      </div>

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
