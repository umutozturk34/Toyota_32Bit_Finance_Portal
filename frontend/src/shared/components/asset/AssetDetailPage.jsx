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

function extractCurrentPrice(asset) {
  if (!asset) return null;
  return asset.priceTry ?? asset.currentPriceTry ?? asset.price ?? asset.currentPrice ?? null;
}

const TRANSFORM_MAP = {
  FUND: transformFundCandles,
};

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
  getBuyProps,
  showBuyButton = true,
  excludeCompare = [],
}) {
  const { t } = useTranslation();
  const goBack = useNavigationBack(backRoute);
  const resolvedLoading = loadingMessage ?? t('marketDetail.loading');
  const resolvedError = errorMessage ?? t('marketDetail.error');
  const resolvedNotFound = notFoundMessage ?? t('marketDetail.notFound');
  const [buyOpen, setBuyOpen] = useState(false);
  const [compareAsset, setCompareAsset] = useState(null);
  const [timeRange, setTimeRange] = useChartRange(`chart-range-${queryKeyPrefix}-${assetCode}`);

  const { data: asset, isLoading, isFetching, error, refetch: refetchAsset } = useQuery({
    queryKey: [queryKeyPrefix, assetCode],
    queryFn: () => fetchAsset(assetCode),
  });

  const { data: historyRaw } = useQuery({
    queryKey: [`${queryKeyPrefix}History`, assetCode, timeRange],
    queryFn: () => fetchHistory(assetCode, timeRange),
    placeholderData: (prev) => prev,
  });

  const compareSymbol = compareAsset?.code || null;

  const { data: compareData } = useQuery({
    queryKey: ['compareHistory', compareAsset?.type, compareSymbol, timeRange],
    queryFn: () => unifiedMarketService.getHistory(compareAsset.type, compareSymbol, timeRange)
      .then(TRANSFORM_MAP[assetType] || transformCandles),
    enabled: !!compareAsset,
  });

  const transform = TRANSFORM_MAP[assetType] || transformCandles;
  const chartData = useMemo(() => transform(historyRaw), [historyRaw, assetType]);

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

      <LightweightChart
        data={chartData}
        symbol={assetCode}
        assetType={chartAssetType || assetType}
        compareData={compareData}
        compareSymbol={compareSymbol}
        timeRange={timeRange}
        onTimeRangeChange={setTimeRange}
      />

      {buyOpen && buyProps && (
        <MarketAddPositionModal
          {...buyProps}
          onClose={() => setBuyOpen(false)}
          onComplete={() => setBuyOpen(false)}
        />
      )}
    </div>
  );
}
