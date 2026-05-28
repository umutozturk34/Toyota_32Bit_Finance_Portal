import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import useChartRange from '../../../shared/hooks/useChartRange';
import { ArrowLeft, Hash, DollarSign, BarChart3, Wallet, Calendar, ExternalLink, Plus } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../../shared/components/feedback/AnimatedIcons';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import { useAssetSeries, useAssetAggregate, useBackfillStatus, isLotPending } from '../hooks/usePortfolioData';
import { formatPercent, changeColors, changeBg, getChangeClass, currentLocaleTag } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { cardVariants } from '../../../shared/utils/animations';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import PositionFormModal from './PositionFormModal';
import MarketOpenDerivativeModal from './MarketOpenDerivativeModal';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import IconButton from '../../../shared/components/buttons/IconButton';
import { buildAssetChartOption, formatChartMoney } from '../lib/assetChartBuilder';
import { resolveNativeCurrency } from '../lib/positionFormHelpers';
import { computeViopAggregate, computeClosedAggregate, computeSeriesEndTs } from '../lib/assetAggregates';
import { LotsTable } from './LotsTable';

const formatEntryDate = (v) => v ? new Date(v).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };
const marketHref = (type, code) => `${TYPE_ROUTES[type] ?? '/market'}/${encodeURIComponent(code)}`;

const STAT_CARD_DEFS = [
  { key: 'quantity', labelKey: 'quantity', Icon: Hash, format: (v) => Number(v).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 6 }) },
  { key: 'entryDate', labelKey: 'entryDate', Icon: Calendar, format: formatEntryDate },
  { key: 'entryPrice', labelKey: 'entryPrice', Icon: DollarSign, money: true },
  { key: 'currentPriceTry', labelKey: 'currentPrice', Icon: BarChart3, money: true },
  { key: 'marketValueTry', labelKey: 'marketValue', Icon: Wallet, money: true },
];

function AssetChart({ data, isDark, t, convertAt, displayCurrency, nativeCurrency }) {
  const option = useMemo(
    () => buildAssetChartOption(data, isDark, t, convertAt, displayCurrency, nativeCurrency),
    [data, isDark, t, convertAt, displayCurrency, nativeCurrency]
  );
  if (!option) return null;
  return <ReactECharts option={option} notMerge lazyUpdate className="h-[260px] sm:h-[300px] lg:h-[340px]" opts={{ renderer: 'canvas' }} />;
}

export default function AssetDetail({ portfolioId, asset, lots = [], onBack, onEditLot, onDeleteLot, onSellLot, onReopenLot, hasActiveDialog = false }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000);
  const { convertAt, currency: displayCurrency } = useRateHistory();
  const [range, setRange] = useChartRange();
  const [addLotOpen, setAddLotOpen] = useState(false);

  const { data: rawSeries = [], isLoading: loading } = useAssetSeries(
    portfolioId, asset.assetType, asset.assetCode, range
  );
  const seriesEndTs = useMemo(() => computeSeriesEndTs(lots), [lots]);
  const series = useMemo(() => {
    if (seriesEndTs == null) return rawSeries;
    return rawSeries.filter((p) => new Date(p.timestamp).getTime() <= seriesEndTs);
  }, [rawSeries, seriesEndTs]);
  const backfill = useBackfillStatus(portfolioId);
  const isUpdating = isLotPending(backfill, asset.assetType, asset.assetCode);

  useEffect(() => {
    if (lots.length === 0 && !isUpdating && !hasActiveDialog) {
      onBack?.();
    }
  }, [lots.length, isUpdating, hasActiveDialog, onBack]);

  const { data: aggregate } = useAssetAggregate(
    portfolioId,
    asset.assetType === 'VIOP' ? null : asset.assetType,
    asset.assetType === 'VIOP' ? null : asset.assetCode,
  );

  const viopAggregate = useMemo(() => computeViopAggregate(asset.assetType, lots), [asset.assetType, lots]);

  const closedAggregate = useMemo(() => computeClosedAggregate(lots), [lots]);

  const effectiveAggregate = (aggregate && aggregate.lotCount > 0 ? aggregate : viopAggregate) || closedAggregate;
  const hasAggregate = effectiveAggregate && effectiveAggregate.lotCount > 0;
  const aggQuantity = hasAggregate ? effectiveAggregate.totalQuantity : asset.quantity;
  const aggEntryDate = hasAggregate ? effectiveAggregate.earliestEntryDate : asset.entryDate;
  const aggEntryPriceTry = hasAggregate ? effectiveAggregate.weightedAvgEntryPrice : asset.entryPrice;
  const aggCurrentPriceTry = hasAggregate ? effectiveAggregate.currentPriceTry : asset.currentPriceTry;
  const aggMarketValueTry = hasAggregate ? effectiveAggregate.totalMarketValueTry : asset.marketValueTry;
  const aggPnlTry = hasAggregate ? effectiveAggregate.totalPnlTry : asset.pnlTry;
  const aggPnlPercent = hasAggregate ? effectiveAggregate.pnlPercent : asset.pnlPercent;
  const lotCount = hasAggregate ? effectiveAggregate.lotCount : (lots.length || 1);

  const entryDateIso = aggEntryDate ? new Date(aggEntryDate).toISOString().slice(0, 10) : null;
  const todayIso = new Date().toISOString().slice(0, 10);
  const nativeCurrency = resolveNativeCurrency(
    { assetType: asset?.type, assetCode: asset?.code, metadata: asset?.metadata },
    asset,
  );
  const targetCurrency = displayCurrency === 'ORIGINAL' || !displayCurrency ? nativeCurrency : displayCurrency;
  const entryPriceConverted = entryDateIso ? convertAt(aggEntryPriceTry, 'TRY', entryDateIso) : null;
  const currentPriceConverted = convertAt(aggCurrentPriceTry, 'TRY', todayIso);
  const marketValueConverted = convertAt(aggMarketValueTry, 'TRY', todayIso);
  const aggAsset = { ...asset, quantity: aggQuantity, entryDate: aggEntryDate };
  const statValueFor = (key) => {
    if (key === 'entryPrice') return formatChartMoney(entryPriceConverted, targetCurrency);
    if (key === 'currentPriceTry') return formatChartMoney(currentPriceConverted, targetCurrency);
    if (key === 'marketValueTry') return formatChartMoney(marketValueConverted, targetCurrency);
    return null;
  };

  const latestPoint = series.length > 0 ? series[series.length - 1] : null;
  const dailyPnlTry = latestPoint?.dailyPnlTry ?? null;
  const dailyPnlPercent = latestPoint?.dailyPnlPercent ?? null;
  const dailyClass = getChangeClass(dailyPnlTry);
  const pnlClass = getChangeClass(aggPnlTry);
  const displayLabel = asset.assetCode;
  const displayBadge = asset.assetImage || null;
  const displayBadgeText = asset.assetCode.replace('.IS', '').slice(0, 3).toUpperCase();
  const anyLotOpen = lots.some((l) => l.exitDate == null);
  const rawAssetName = asset.assetName || t(`assets.labels.${asset.assetType}`, { defaultValue: asset.assetType });
  const displaySub = anyLotOpen
    ? rawAssetName.replace(/\s*·\s*KAPALI\s*$/i, '')
    : rawAssetName;

  return (
    <div className="space-y-5">
      <motion.div
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between gap-3 flex-wrap"
      >
        <div className="flex items-center gap-3 min-w-0">
          <IconButton
            variant="secondary"
            size={9}
            shape="square"
            icon={<ArrowLeft className="h-4 w-4" />}
            aria-label={t('common.back')}
            onClick={onBack}
            className="hover:-translate-x-0.5 shrink-0"
          />
          <div className="flex items-center gap-3 min-w-0">
            {displayBadge ? (
              /^https?:\/\//i.test(displayBadge)
                ? <img src={displayBadge} alt={displayLabel} className="w-10 h-10 rounded-xl" />
                : <span className="flex items-center justify-center w-10 h-10 rounded-xl text-2xl">{displayBadge}</span>
            ) : (
              <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-sm font-bold text-accent">
                {displayBadgeText}
              </span>
            )}
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-lg sm:text-xl font-bold text-fg truncate max-w-[180px] sm:max-w-none">{displayLabel}</h1>
                {lotCount > 1 && (
                  <span className="inline-flex items-center rounded-md bg-accent/10 text-accent text-[10px] font-bold px-1.5 py-0.5 shrink-0">
                    {t('assetDetail.lotCount', { count: lotCount, defaultValue: '{{count}} lot' })}
                  </span>
                )}
              </div>
              <p className="text-xs text-fg-muted truncate">{displaySub}</p>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <button
            type="button"
            onClick={() => navigate(marketHref(asset.assetType, asset.assetCode))}
            className="flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-2.5 sm:px-3 py-2 text-xs font-semibold text-fg-muted hover:text-accent hover:border-accent/40 hover:bg-accent/5 transition-all backdrop-blur-sm cursor-pointer"
            title={t('portfolio.positions.viewOnMarket', { defaultValue: 'Pazar detayı' })}
          >
            <ExternalLink className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{t('portfolio.positions.viewOnMarket', { defaultValue: 'Pazar detayı' })}</span>
          </button>
          <button
            onClick={() => setAddLotOpen(true)}
            className="flex items-center gap-2 rounded-lg bg-accent px-3 sm:px-4 py-2 text-sm font-semibold text-white hover:bg-accent-bright transition-all border-none cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            {t('assetDetail.newLot')}
          </button>
        </div>
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3"
      >
        {STAT_CARD_DEFS.map(({ key, labelKey, Icon, format, money: isMoney }) => (
          <Card key={key} variant="elevated" radius="xl" padding="sm" interactive className="space-y-2">
            <div className="flex items-center gap-2">
              <div className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/10">
                <Icon className="h-3 w-3 text-accent" />
              </div>
              <p className="text-[11px] text-fg-muted">{t(`assetDetail.stats.${labelKey}`)}</p>
            </div>
            <p className="text-sm font-semibold font-mono text-fg">{isMoney ? statValueFor(key) : format(aggAsset[key])}</p>
          </Card>
        ))}
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 sm:grid-cols-2 gap-3"
      >
        <Card variant="outline" tone={aggPnlTry >= 0 ? 'success' : 'danger'} radius="xl" padding="md" className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {aggPnlTry >= 0
              ? <TrendingUp className="h-5 w-5 text-success" />
              : <TrendingDown className="h-5 w-5 text-danger" />}
            <span className="text-sm font-medium text-fg">{t('assetDetail.pnl')}</span>
          </div>
          <div className="text-right flex items-center gap-3">
            <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
              {formatPercent(aggPnlPercent)}
            </span>
            <p className={`text-lg font-semibold font-mono ${changeColors[pnlClass]}`}>
              {money(aggPnlTry)}
            </p>
          </div>
        </Card>
        <Card variant={dailyPnlTry == null ? 'elevated' : 'outline'} tone={dailyPnlTry == null ? 'default' : (dailyPnlTry >= 0 ? 'success' : 'danger')} radius="xl" padding="md" className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {dailyPnlTry == null
              ? <TrendingUp className="h-5 w-5 text-fg-muted" />
              : dailyPnlTry >= 0
                ? <TrendingUp className="h-5 w-5 text-success" />
                : <TrendingDown className="h-5 w-5 text-danger" />}
            <span className="text-sm font-medium text-fg">{t('assetDetail.dailyPnl')}</span>
          </div>
          <div className="text-right flex items-center gap-3">
            {dailyPnlTry == null ? (
              <p className="text-sm text-fg-muted">—</p>
            ) : (
              <>
                <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[dailyClass]} ${changeColors[dailyClass]}`}>
                  {formatPercent(dailyPnlPercent)}
                </span>
                <p className={`text-lg font-semibold font-mono ${changeColors[dailyClass]}`}>
                  {money(dailyPnlTry)}
                </p>
              </>
            )}
          </div>
        </Card>
      </motion.div>

      <Card
        as={motion.div}
        variants={cardVariants}
        initial="hidden"
        animate="show"
        variant="elevated"
        radius="xl"
        padding="md"
        backdropBlur
        className="space-y-3"
      >
        <div className="flex items-center justify-between flex-wrap gap-2">
          {lots.length > 0 ? (
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1.5">
                <span className="relative w-2 h-2">
                  <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-30" />
                  <span className="relative block w-2 h-2 rounded-full bg-success" />
                </span>
                <span className="text-[10px] text-fg-muted font-medium">
                  {t(asset.assetType === 'VIOP'
                    ? 'assetDetail.lots.lotMarkerOpen'
                    : 'portfolio.performance.lotAdded')}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="relative w-2 h-2">
                  <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-30" />
                  <span className="relative block w-2 h-2 rounded-full bg-danger" />
                </span>
                <span className="text-[10px] text-fg-muted font-medium">
                  {t(asset.assetType === 'VIOP'
                    ? 'assetDetail.lots.lotMarkerClose'
                    : 'portfolio.performance.lotSoldOrClosed')}
                </span>
              </div>
            </div>
          ) : <span />}
          <RangeSelector value={range} onChange={setRange} layoutId="asset-range" size="sm" />
        </div>

        <div className="relative">
          {loading && (
            <div className="absolute inset-0 flex items-center justify-center z-10 bg-bg-elevated/60 rounded-lg">
              <Spinner size="md" tone="accent" />
            </div>
          )}
          {isUpdating && !loading && (
            <div className="absolute top-2 right-2 z-10 flex items-center gap-1.5 bg-accent/15 text-accent text-[11px] font-semibold px-2.5 py-1 rounded-full backdrop-blur-sm">
              <Spinner size="xs" tone="inherit" />
              {t('assetDetail.updating', { defaultValue: 'Güncelleniyor...' })}
            </div>
          )}
          {series.length === 0 && !loading ? (
            <div className="flex items-center justify-center h-[340px] text-sm text-fg-muted">
              {t('assetDetail.noDataInRange')}
            </div>
          ) : series.length > 0 ? (
            <AssetChart data={series} isDark={isDark} t={t} convertAt={convertAt} displayCurrency={displayCurrency} nativeCurrency={nativeCurrency} />
          ) : null}
        </div>
      </Card>

      {lots.length > 0 && (
        <Card
          as={motion.div}
          variants={cardVariants}
          initial="hidden"
          animate="show"
          variant="elevated"
          radius="xl"
          padding="md"
          backdropBlur
          className="space-y-3"
        >
          <LotsTable
            lots={lots}
            t={t}
            money={money}
            bigMoney={bigMoney}
            onEditLot={onEditLot}
            onSellLot={onSellLot}
            onReopenLot={onReopenLot}
            onDeleteLot={onDeleteLot}
          />
        </Card>
      )}

      {addLotOpen && asset.assetType === 'VIOP' && (
        <MarketOpenDerivativeModal
          assetCode={asset.assetCode}
          assetName={asset.assetName}
          currentPrice={asset.currentPriceTry}
          metadata={asset.metadata}
          onClose={() => setAddLotOpen(false)}
          onComplete={() => setAddLotOpen(false)}
        />
      )}
      {addLotOpen && asset.assetType !== 'VIOP' && (
        <PositionFormModal
          mode="add"
          portfolioId={portfolioId}
          asset={{
            type: asset.assetType,
            code: asset.assetCode,
            name: asset.assetName,
            image: asset.assetImage,
            currentPrice: asset.currentPriceTry,
          }}
          onClose={() => setAddLotOpen(false)}
          onComplete={() => setAddLotOpen(false)}
        />
      )}
    </div>
  );
}
