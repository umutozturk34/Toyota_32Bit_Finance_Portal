import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { ShoppingCart } from '../feedback/AnimatedIcons';
import useChartRange from '../../hooks/useChartRange';
import useNavigationBack from '../../hooks/useNavigationBack';
import Button from '../buttons/Button';
import IconButton from '../buttons/IconButton';
import { unifiedMarketService } from '../../services/unifiedMarketService';
import LoadingState from '../feedback/LoadingState';
import ErrorState from '../feedback/ErrorState';
import MarketAddPositionModal from '../../../features/portfolio/components/MarketAddPositionModal';
import CompareBar from '../layout/CompareBar';
import LightweightChart from '../../../features/chart/components/LightweightChart';
import AssetActionsBar from '../../../features/watch/components/AssetActionsBar';
import MarketStatusBadge from '../layout/MarketStatusBadge';
import { transformCandles, transformFundCandles } from '../../utils/candleTransform';
import { useRateHistory } from '../../hooks/useRateHistory';

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

function naturalCurrencyFor(assetType, asset) {
  if (assetType === 'VIOP') return asset?.metadata?.currency || 'TRY';
  if (assetType === 'CRYPTO') return 'USD';
  if (assetType === 'COMMODITY' && asset?.code && asset.code.toUpperCase().endsWith('USD')) return 'USD';
  return 'TRY';
}

function convertCandleSet(data, convertAt, baseCurrency, naturalCurrency) {
  if (!data?.candles) return data;
  return {
    ...data,
    candles: data.candles.map((candle) => {
      const date = candle.candleDate || candle.date;
      const next = { ...candle };
      for (const field of CANDLE_MONEY_FIELDS) {
        if (next[field] != null) next[field] = convertAt(next[field], baseCurrency, date, naturalCurrency);
      }
      return next;
    }),
  };
}

const RANGE_DAYS = { '1W': 7, '1M': 31, '3M': 93, '6M': 186, '1Y': 372, '5Y': 1830 };
const CLIENT_FILTER_RANGES = new Set(['1W', '1M', '3M', '6M', '1Y']);

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
  renderSidebar,
  getBuyProps,
  showBuyButton = true,
  excludeCompare = [],
  buyModalComponent: BuyModalComponent = MarketAddPositionModal,
  clientSideRangeFilter = false,
}) {
  const { t } = useTranslation();
  const { convertAt } = useRateHistory();
  const goBack = useNavigationBack(backRoute);
  const resolvedLoading = loadingMessage ?? t('marketDetail.loading');
  const resolvedError = errorMessage ?? t('marketDetail.error');
  const resolvedNotFound = notFoundMessage ?? t('marketDetail.notFound');
  const [buyOpen, setBuyOpen] = useState(false);
  const [compareAsset, setCompareAsset] = useState(null);
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

  const compareSymbol = compareAsset?.code || null;

  const { data: compareData } = useQuery({
    queryKey: ['compareHistory', compareAsset?.type, compareSymbol, timeRange],
    queryFn: () => unifiedMarketService.getHistory(compareAsset.type, compareSymbol, timeRange)
      .then(TRANSFORM_MAP[assetType] || transformCandles),
    enabled: !!compareAsset,
  });

  const transform = TRANSFORM_MAP[assetType] || transformCandles;
  const baseCurrency = asset?.metadata?.currency || 'TRY';
  const naturalCurrency = naturalCurrencyFor(assetType, asset);
  const chartData = useMemo(
    () => convertCandleSet(transform(filteredHistoryRaw), convertAt, baseCurrency, naturalCurrency),
    [filteredHistoryRaw, transform, convertAt, baseCurrency, naturalCurrency],
  );
  const convertedCompareData = useMemo(
    () => convertCandleSet(compareData, convertAt, 'TRY', 'TRY'),
    [compareData, convertAt],
  );

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
    <div className="space-y-6">
      <motion.div
        initial={{ opacity: 0, y: -12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between"
      >
        <div className="flex items-center gap-3">
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
        <div className="flex items-center gap-2">
          {asset && (
            <AssetActionsBar
              marketType={assetType}
              assetCode={assetCode}
              currentPrice={extractCurrentPrice(asset)}
            />
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

      {renderMetadata(asset)}

      <div className="flex items-center gap-3">
        <CompareBar
          compareAsset={compareAsset}
          onSelect={setCompareAsset}
          onClear={() => setCompareAsset(null)}
          excludeCodes={[assetCode, ...excludeCompare]}
        />
      </div>

      {renderSidebar ? (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
          <LightweightChart
            data={chartData}
            symbol={assetCode}
            assetType={chartAssetType || assetType}
            compareData={convertedCompareData}
            compareSymbol={compareSymbol}
            timeRange={effectiveRange}
            onTimeRangeChange={setTimeRange}
          />
          <aside className="xl:sticky xl:top-4 xl:self-start space-y-3">
            {renderSidebar(asset)}
          </aside>
        </div>
      ) : (
        <LightweightChart
          data={chartData}
          symbol={assetCode}
          assetType={chartAssetType || assetType}
          compareData={convertedCompareData}
          compareSymbol={compareSymbol}
          timeRange={effectiveRange}
          onTimeRangeChange={setTimeRange}
        />
      )}

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
