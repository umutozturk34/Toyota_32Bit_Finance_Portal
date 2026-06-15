import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import ReactECharts from 'echarts-for-react';
import {
  ArrowLeft,
  Landmark,
  Calendar,
  Percent,
  TrendingUp,
  TrendingDown,
  Building2,
  Clock,
  ChevronDown,
  ChevronUp,
  Plus,
  Repeat,
} from 'lucide-react';
import { bondService } from './services/bondService';
import { inferCouponCadence } from './lib/couponCadence';
import AssetRelatedNews from '../news/components/AssetRelatedNews';
import { useTheme } from '../../shared/context/useTheme';
import useSessionState from '../../shared/hooks/useSessionState';
import useNavigationBack from '../../shared/hooks/useNavigationBack';
import IconButton from '../../shared/components/buttons/IconButton';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import MarketStatusBadge from '../../shared/components/layout/MarketStatusBadge';
import MarketAddBondModal from '../portfolio/components/MarketAddBondModal';
import { BOND_TYPE_COLORS, CHART_LINE_COLORS, CHART_RATE_COLORS, DEFAULT_RATE_COLOR } from './lib/bondConstants';

function formatRate(val) {
  if (val == null) return '—';
  return `%${Number(val).toFixed(2)}`;
}

function formatPrice(val, localeTag = 'en-US') {
  if (val == null) return '—';
  return new Intl.NumberFormat(localeTag, { minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(val);
}

function formatDate(val, localeTag = 'en-US') {
  if (!val) return '—';
  return new Date(val).toLocaleDateString(localeTag, { timeZone: 'Europe/Istanbul' });
}

function daysUntil(dateStr) {
  if (!dateStr) return null;
  return Math.ceil((new Date(dateStr) - new Date()) / (1000 * 60 * 60 * 24));
}

function isFloatingType(bondType) {
  return ['FLOATING_TLREF', 'FLOATING_CPI', 'FLOATING_AUCTION', 'SUKUK_CPI'].includes(bondType);
}

function StatCell({ icon: Icon, label, value, mono }) {
  return (
    <div className="space-y-1">
      <span className="flex items-center gap-1.5 text-xs text-fg-muted">
        <Icon className="h-3.5 w-3.5" /> {label}
      </span>
      <span className={`block text-sm ${mono ? 'font-mono' : 'font-medium'} text-fg`}>{value}</span>
    </div>
  );
}

function buildLineOption({ history, valueKey, color, isDark, valueFormatter }) {
  const rows = history
    .filter((d) => d[valueKey] != null)
    .map((d) => [new Date(d.date).getTime(), Number(d[valueKey])]);

  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const showZoom = rows.length >= 2;

  return {
    backgroundColor: 'transparent',
    animation: true,
    grid: { left: 56, right: 16, top: 16, bottom: showZoom ? 48 : 24, containLabel: false },
    dataZoom: showZoom ? [
      { type: 'inside', filterMode: 'filter' },
      {
        type: 'slider',
        height: 16,
        bottom: 6,
        filterMode: 'filter',
        borderColor: 'transparent',
        backgroundColor: 'transparent',
        fillerColor: 'rgba(99,102,241,0.12)',
        handleStyle: { color: '#6366f1', borderColor: '#6366f1' },
        showDetail: false,
        brushSelect: false,
        textStyle: { color: muted, fontSize: 9 },
      },
    ] : undefined,
    tooltip: {
      trigger: 'axis',
      confine: true,
      position: (point, _params, _dom, _rect, size) => {
        const x = Math.max(8, Math.min(point[0] - size.contentSize[0] / 2, size.viewSize[0] - size.contentSize[0] - 8));
        return [x, 8];
      },
      backgroundColor: tooltipBg,
      borderWidth: 0,
      textStyle: { color: tooltipFg, fontSize: 11 },
      valueFormatter,
    },
    xAxis: {
      type: 'time',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 10 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 10, formatter: valueFormatter },
      splitLine: { lineStyle: { color: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)' } },
    },
    series: [{
      type: 'line',
      smooth: rows.length < 200,
      showSymbol: false,
      sampling: 'lttb',
      data: rows,
      lineStyle: { color, width: 2 },
      itemStyle: { color },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${color}55` },
            { offset: 1, color: `${color}00` },
          ],
        },
      },
    }],
    media: [{
      query: { maxWidth: 640 },
      option: {
        grid: { left: 32, right: 8, top: 12, bottom: showZoom ? 44 : 18 },
        xAxis: { axisLabel: { fontSize: 9, rotate: 30 } },
        yAxis: { axisLabel: { fontSize: 9 } },
        dataZoom: showZoom ? [{}, { height: 22, bottom: 4 }] : undefined,
      },
    }],
  };
}

export default function BondDetail() {
  const { seriesCode } = useParams();
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const goBack = useNavigationBack('/bonds');
  const localeTag = t('common.localeTag');
  const [rateOpen, setRateOpen] = useSessionState('bond-detail-rate-open', true);
  const [addOpen, setAddOpen] = useState(false);

  const { data: bond, isLoading: bondLoading, error: bondError, refetch } = useQuery({
    queryKey: ['bond', seriesCode],
    queryFn: () => bondService.getBondByCode(seriesCode),
  });

  const { data: history = [], isLoading: historyLoading } = useQuery({
    queryKey: ['bondRateHistory', bond?.isinCode],
    queryFn: () => bondService.getRateHistory(bond.isinCode),
    enabled: !!bond?.isinCode,
  });

  const priceColor = bond ? (CHART_LINE_COLORS[bond.bondType] || '#8b5cf6') : '#8b5cf6';
  const rateColor = bond ? (CHART_RATE_COLORS[bond.bondType] || DEFAULT_RATE_COLOR) : DEFAULT_RATE_COLOR;
  const latestPrice = useMemo(() => {
    for (let i = history.length - 1; i >= 0; i--) {
      if (history[i].price != null) return Number(history[i].price);
    }
    return bond?.baseIndex != null ? Number(bond.baseIndex) : null;
  }, [history, bond?.baseIndex]);
  // Clean-price change vs the previous observation in the rate history — the bond's day-over-day move. Null when
  // there are fewer than two priced points (a freshly-listed or sparsely-quoted bond).
  const priceChangePct = useMemo(() => {
    const priced = history.filter((d) => d.price != null);
    if (priced.length < 2) return null;
    const last = Number(priced[priced.length - 1].price);
    const prev = Number(priced[priced.length - 2].price);
    if (!(prev > 0)) return null;
    return ((last - prev) / prev) * 100;
  }, [history]);
  const latestRate = useMemo(() => {
    for (let i = history.length - 1; i >= 0; i--) {
      if (history[i].rate != null) return Number(history[i].rate);
    }
    return bond?.couponRate != null ? Number(bond.couponRate) : null;
  }, [history, bond?.couponRate]);
  // Coupon rhythm read off the price-history SHAPE (jumps), not the declared schedule — most telling for CPI/gold
  // linkers whose indexed price steps at each coupon. Null (→ nothing rendered) when the price shows no jump.
  const cadence = useMemo(() => inferCouponCadence(history, bond?.maturityStart), [history, bond?.maturityStart]);
  const priceOption = useMemo(
    () => buildLineOption({
      history, valueKey: 'price', color: priceColor, isDark,
      valueFormatter: (v) => formatPrice(v, localeTag),
    }),
    [history, priceColor, isDark, localeTag],
  );
  const rateOption = useMemo(
    () => buildLineOption({
      history, valueKey: 'rate', color: rateColor, isDark,
      valueFormatter: formatRate,
    }),
    [history, rateColor, isDark],
  );

  if (bondLoading) return <LoadingState message={t('market.bond.loading')} />;
  if (bondError || !bond) {
    return <ErrorState message={t('market.bond.error')} onRetry={refetch} />;
  }

  const typeColor = BOND_TYPE_COLORS[bond.bondType] || 'bg-accent/10 text-accent border-accent/20';
  const maturityDays = daysUntil(bond.maturityEnd);
  const couponDays = daysUntil(bond.nextCouponDate);
  const hasPrice = history.some((d) => d.price != null);
  const hasRate = history.some((d) => d.rate != null);

  return (
    <div className="space-y-6 py-6" data-tour="bond-detail-card">
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="flex items-center justify-between gap-3 flex-wrap"
      >
        <div className="flex items-center gap-3 min-w-0 flex-wrap">
          <IconButton
            variant="secondary"
            size={9}
            shape="square"
            icon={<ArrowLeft className="h-4 w-4" />}
            aria-label={t('common.back', { defaultValue: 'back' })}
            onClick={goBack}
          />
          <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-accent shrink-0">
            <Landmark className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <h1 className="text-base sm:text-xl font-bold text-fg leading-tight truncate">{bond.isinCode}</h1>
            <p className="text-[10px] sm:text-xs font-mono text-fg-muted mt-0.5 truncate">{bond.seriesCode}</p>
          </div>
          <MarketStatusBadge market="BOND" compact />
        </div>
        <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
          <button
            type="button"
            onClick={() => setAddOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-1.5 text-xs sm:text-sm font-semibold text-white hover:bg-accent-bright transition-colors cursor-pointer border-none"
          >
            <Plus className="h-3.5 w-3.5" />
            {t('portfolio.bonds.addAction', { defaultValue: 'Tahvil Ekle' })}
          </button>
          <span className={`rounded-lg border px-2 sm:px-3 py-1 text-[10px] sm:text-xs font-semibold tracking-wider ${typeColor}`}>
            {t(`market.bond.types.${bond.bondType}`, { defaultValue: bond.bondType })}
          </span>
          <div className="flex items-center gap-2">
            <span className="font-mono text-lg sm:text-2xl font-bold text-fg">{formatPrice(bond.baseIndex, localeTag)}</span>
            {priceChangePct != null && (
              <span className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs sm:text-sm font-semibold ${priceChangePct >= 0 ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'}`}>
                {priceChangePct >= 0 ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
                {priceChangePct >= 0 ? '+' : ''}{priceChangePct.toFixed(2)}%
              </span>
            )}
          </div>
        </div>
      </motion.div>

      <div className="rounded-2xl border border-border-default bg-bg-elevated p-6">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4">
          <StatCell icon={Building2} label={t('market.bond.issuerLabel')} value={(!bond.issuer || bond.issuer === 'HAZİNE' || bond.issuer === 'TREASURY') ? t('market.bond.treasuryFallback') : bond.issuer} />
          <StatCell icon={Percent} label={t('market.bond.couponRate')} value={formatRate(bond.couponRate)} mono />
          <StatCell icon={TrendingUp} label={t('market.bond.simpleYield')} value={
            isFloatingType(bond.bondType)
              ? <span className="text-warning font-medium">{t('market.bond.floating')}</span>
              : formatRate(bond.simpleYield)
          } mono />
          <StatCell icon={Calendar} label={t('market.bond.startLabel')} value={formatDate(bond.maturityStart, localeTag)} mono />
          <StatCell icon={Calendar} label={t('market.bond.maturityLabel')} value={
            <>{formatDate(bond.maturityEnd, localeTag)}{maturityDays != null && <span className="ml-1.5 text-fg-subtle">({maturityDays}{t('market.bond.daysSuffix')})</span>}</>
          } mono />
          {bond.nextCouponDate && (
            <StatCell icon={Calendar} label={t('market.bond.couponDateLabel')} value={
              <>{formatDate(bond.nextCouponDate, localeTag)}{couponDays != null && <span className="ml-1.5 text-fg-subtle">({couponDays}{t('market.bond.daysSuffix')})</span>}</>
            } mono />
          )}
        </div>
        <div className="mt-4 flex items-center gap-1.5 text-[11px] text-fg-subtle">
          <Clock className="h-3 w-3" />
          {bond.lastUpdated ? new Date(bond.lastUpdated).toLocaleString(localeTag, { timeZone: 'Europe/Istanbul' }) : '—'}
        </div>
      </div>

      <div className="rounded-2xl border border-border-default bg-bg-elevated overflow-hidden">
        <div className="px-5 pt-4 pb-2 flex items-center justify-between gap-3 flex-wrap">
          <h2 className="text-sm font-semibold text-fg">{t('market.bond.priceChartTitle', { defaultValue: 'Fiyat geçmişi' })}</h2>
          {latestPrice != null && (
            <span className="font-mono text-lg sm:text-xl font-bold text-fg tabular-nums">
              {formatPrice(latestPrice, localeTag)}
            </span>
          )}
        </div>
        <div className="px-3 pb-4">
          {historyLoading ? (
            <div className="h-72 flex items-center justify-center text-fg-muted text-xs">{t('market.bond.chartLoading')}</div>
          ) : hasPrice ? (
            <ReactECharts option={priceOption} style={{ height: 'min(55vh, 320px)', minHeight: 220, width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
          ) : (
            <div className="h-72 flex items-center justify-center text-fg-muted text-xs">{t('market.bond.noPriceData', { defaultValue: 'Fiyat geçmişi yok' })}</div>
          )}
        </div>

        {cadence && (cadence.cadenceKey || (cadence.firstJumpDaysFromStart != null && cadence.firstJumpDaysFromStart >= 0)) && (
          <div className="px-5 pb-4 -mt-1 flex items-start gap-2 text-[11px] text-fg-subtle">
            <Repeat className="h-3.5 w-3.5 mt-0.5 shrink-0 text-fg-muted" />
            <span>
              {cadence.cadenceKey ? (
                <>
                  <span className="font-medium text-fg-muted">{t('market.bond.cadence.title')}:</span>{' '}
                  {t('market.bond.cadence.everyDays', { days: cadence.approxDays })}{' '}
                  ({t(`market.bond.cadence.labels.${cadence.cadenceKey}`)})
                  {cadence.firstJumpDaysFromStart != null && cadence.firstJumpDaysFromStart >= 0 && (
                    <> · {t('market.bond.cadence.firstJump', { n: cadence.firstJumpDaysFromStart })}</>
                  )}
                </>
              ) : (
                t('market.bond.cadence.jumpOnly', { n: cadence.firstJumpDaysFromStart })
              )}
            </span>
          </div>
        )}

        <div className="border-t border-border-default">
          <button
            type="button"
            data-tour="bond-coupon-toggle"
            onClick={() => setRateOpen((v) => !v)}
            className="w-full px-5 py-3 flex items-center justify-between gap-3 text-sm font-semibold text-fg hover:bg-bg-base/40 transition-colors cursor-pointer bg-transparent border-none"
          >
            <span className="flex items-center gap-2">
              <Percent className="h-3.5 w-3.5 text-fg-muted" />
              {t('market.bond.rateChartTitle', { defaultValue: 'Kupon oranı geçmişi' })}
            </span>
            <span className="flex items-center gap-3">
              {latestRate != null && (
                <span className="font-mono text-base sm:text-lg font-bold text-fg tabular-nums">{formatRate(latestRate)}</span>
              )}
              {rateOpen ? <ChevronUp className="h-4 w-4 text-fg-muted" /> : <ChevronDown className="h-4 w-4 text-fg-muted" />}
            </span>
          </button>
          <AnimatePresence initial={false}>
            {rateOpen && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.25 }}
                className="overflow-hidden"
              >
                <div className="px-3 pb-4" data-tour="bond-coupon-chart">
                  {historyLoading ? (
                    <div className="h-56 flex items-center justify-center text-fg-muted text-xs">{t('market.bond.chartLoading')}</div>
                  ) : hasRate ? (
                    <ReactECharts option={rateOption} style={{ height: 'min(45vh, 260px)', minHeight: 200, width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
                  ) : (
                    <div className="h-56 flex items-center justify-center text-fg-muted text-xs">{t('market.bond.noRateData')}</div>
                  )}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      <AssetRelatedNews assetCode={bond.isinCode} assetName={bond.seriesCode} assetType="BOND" />

      {addOpen && (
        <MarketAddBondModal
          bond={bond}
          onClose={() => setAddOpen(false)}
          onComplete={() => setAddOpen(false)}
        />
      )}
    </div>
  );
}
