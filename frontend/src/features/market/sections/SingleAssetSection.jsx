import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { useInView, motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { getChangeClass, changeColors, changeBg, formatPercent, formatVolume } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { priceCurrencyOf } from '../../../shared/utils/priceCurrency';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import useNavigationStore from '../../../shared/stores/useNavigationStore';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { macroIndicatorService } from '../../macro/services/macroIndicatorService';
import { STALE } from '../../../shared/constants/query';
import Card from '../../../shared/components/card';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };
const RANGES = ['1W', '1M', '3M', '6M', '1Y', '5Y'];
const RANGE_DAYS = { '1W': 7, '1M': 30, '3M': 90, '6M': 180, '1Y': 365, '5Y': 1825 };
const MAX_CANDLES = 280;
const MAX_ANIMATION_MS = 1800;

function isMacroType(type) {
    return typeof type === 'string' && type.startsWith('MACRO');
}

async function fetchAssetHistory(type, code, range) {
    if (isMacroType(type)) {
        const days = RANGE_DAYS[range] ?? 30;
        const to = new Date();
        const from = new Date(to);
        from.setDate(from.getDate() - days);
        const iso = (d) => d.toISOString().slice(0, 10);
        const points = await macroIndicatorService.history(code, { from: iso(from), to: iso(to) });
        return (points || []).map((p) => ({ date: p.observedAt, close: Number(p.value) }));
    }
    return unifiedMarketService.getHistory(type, code, range);
}

function aggregateCandles(data, maxBars) {
    if (data.length <= maxBars) return data;
    const bucket = Math.ceil(data.length / maxBars);
    const out = [];
    for (let i = 0; i < data.length; i += bucket) {
        const chunk = data.slice(i, i + bucket);
        if (chunk.length === 0) continue;
        const t = chunk[0][0];
        const o = chunk[0][1];
        const cl = chunk[chunk.length - 1][2];
        let lo = chunk[0][3];
        let hi = chunk[0][4];
        for (let j = 1; j < chunk.length; j += 1) {
            if (chunk[j][3] < lo) lo = chunk[j][3];
            if (chunk[j][4] > hi) hi = chunk[j][4];
        }
        out.push([t, o, cl, lo, hi]);
    }
    return out;
}

function buildChartOption(history, chartType, accent, isDark, labels) {
    if (!Array.isArray(history) || history.length === 0) return null;
    const raw = history
        .map((c) => {
            const t = c.candleDate || c.date;
            const o = Number(c.open ?? c.price ?? c.close);
            const h = Number(c.high ?? c.price ?? c.close);
            const l = Number(c.low ?? c.price ?? c.close);
            const cl = Number(c.close ?? c.price);
            if (!t || !Number.isFinite(cl)) return null;
            return [new Date(t).getTime(), o, cl, l, h];
        })
        .filter(Boolean)
        .sort((a, b) => a[0] - b[0]);
    if (raw.length === 0) return null;
    const data = chartType === 'candle' ? aggregateCandles(raw, MAX_CANDLES) : raw;

    const muted = isDark ? '#6b6b7a' : '#94a3b8';
    const grid = isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.05)';
    const upColor = '#10b981';
    const downColor = '#ef4444';
    const isCandle = chartType === 'candle';
    const animationDuration = isCandle ? 420 : 1200;
    const perItemDelay = isCandle && data.length > 0
        ? Math.max(0.5, Math.min(4, (MAX_ANIMATION_MS - animationDuration) / data.length))
        : 0;

    const series = isCandle
        ? [{
            type: 'candlestick',
            data: data.map((d) => [d[1], d[2], d[3], d[4]]),
            itemStyle: { color: upColor, color0: downColor, borderColor: upColor, borderColor0: downColor },
            barMaxWidth: 14,
            animationThreshold: 5000,
            progressive: 0,
        }]
        : [{
            type: 'line',
            smooth: data.length < 200,
            showSymbol: false,
            sampling: 'lttb',
            data: data.map((d) => [d[0], d[2]]),
            lineStyle: { color: accent, width: 1.8 },
            areaStyle: {
                color: {
                    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                    colorStops: [
                        { offset: 0, color: `${accent}55` },
                        { offset: 1, color: `${accent}00` },
                    ],
                },
            },
            animationThreshold: 5000,
        }];

    return {
        animation: true,
        animationDuration,
        animationEasing: 'cubicOut',
        animationDelay: (idx) => idx * perItemDelay,
        backgroundColor: 'transparent',
        grid: { left: 8, right: 8, top: 12, bottom: 28, containLabel: true },
        xAxis: {
            type: isCandle ? 'category' : 'time',
            data: isCandle ? data.map((d) => new Date(d[0]).toLocaleDateString()) : undefined,
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { color: muted, fontSize: 11, hideOverlap: true, alignMinLabel: 'left', alignMaxLabel: 'right' },
            splitLine: { show: false },
        },
        yAxis: {
            type: 'value',
            scale: true,
            position: 'right',
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { color: muted, fontSize: 11 },
            splitLine: { lineStyle: { color: grid, type: 'dashed' } },
        },
        tooltip: {
            trigger: 'axis',
            confine: true,
            appendToBody: true,
            backgroundColor: isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)',
            borderWidth: 0,
            textStyle: { color: isDark ? '#e2e2ea' : '#1a1a2e', fontSize: 12 },
            formatter: (params) => {
                if (!params || params.length === 0) return '';
                const p = params[0];
                const fg = isDark ? '#e2e2ea' : '#1a1a2e';
                const muteTip = isDark ? '#6b6b7a' : '#94a3b8';
                if (isCandle) {
                    const arr = Array.isArray(p.value) ? p.value : [];
                    const [o, c, l, h] = [arr[1], arr[2], arr[3], arr[4]];
                    const fmt = (v) => Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 4 });
                    return `<div style="padding:4px 2px;min-width:160px">
                        <div style="color:${muteTip};font-size:10px;margin-bottom:4px">${p.name}</div>
                        <div style="display:flex;justify-content:space-between;gap:14px"><span style="color:${muteTip}">${labels.open}</span><span style="color:${fg};font-weight:600">${fmt(o)}</span></div>
                        <div style="display:flex;justify-content:space-between;gap:14px"><span style="color:${muteTip}">${labels.close}</span><span style="color:${fg};font-weight:600">${fmt(c)}</span></div>
                        <div style="display:flex;justify-content:space-between;gap:14px"><span style="color:${muteTip}">${labels.low}</span><span style="color:${fg};font-weight:600">${fmt(l)}</span></div>
                        <div style="display:flex;justify-content:space-between;gap:14px"><span style="color:${muteTip}">${labels.high}</span><span style="color:${fg};font-weight:600">${fmt(h)}</span></div>
                    </div>`;
                }
                const arr = Array.isArray(p.value) ? p.value : [];
                const v = arr[1];
                const fmt = (val) => Number(val).toLocaleString('tr-TR', { maximumFractionDigits: 4 });
                const date = new Date(arr[0]).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
                return `<div style="padding:4px 2px;min-width:140px">
                    <div style="color:${muteTip};font-size:10px;margin-bottom:4px">${date}</div>
                    <div style="display:flex;justify-content:space-between;gap:14px"><span style="color:${muteTip}">${labels.close}</span><span style="color:${fg};font-weight:600">${fmt(v)}</span></div>
                </div>`;
            },
        },
        series,
    };
}

function buildMetadataRows(asset, latestCandle, t, money, priceCcy) {
    const meta = asset?.metadata || {};
    const rows = [];
    const priceFmt = (v) => money(v, priceCcy);
    const percentFmt = (v) => `${Number(v) >= 0 ? '+' : ''}${Number(v).toFixed(2)}%`;
    const passthrough = (v) => String(v);
    const push = (key, value, formatter = priceFmt) => {
        if (value == null || value === '') return;
        rows.push({ label: t(`singleAssetSection.${key}`, { defaultValue: key.toUpperCase() }), value: formatter(value) });
    };

    switch (asset?.type) {
        case 'FOREX':
            push('buying', meta.buyingPrice);
            push('selling', meta.sellingPrice);
            push('effectiveBuying', meta.effectiveBuyingPrice);
            push('effectiveSelling', meta.effectiveSellingPrice);
            break;
        case 'CRYPTO':
            push('marketCap', meta.marketCap, formatVolume);
            push('volume', meta.totalVolume, formatVolume);
            push('priceUsd', meta.currentPriceUsd, (v) => money(v, 'USD'));
            push('high', latestCandle?.high);
            push('low', latestCandle?.low);
            break;
        case 'FUND':
            push('risk', meta.riskValue, (v) => `${v}/7`);
            push('category', meta.category, passthrough);
            push('return1y', meta.return1y, percentFmt);
            push('portfolioSize', meta.portfolioSize, formatVolume);
            break;
        case 'VIOP':
            push('bid', meta.bid);
            push('ask', meta.ask);
            push('strike', meta.strikePrice);
            push('expiry', meta.expiryDate, passthrough);
            break;
        case 'COMMODITY':
            push('open', meta.openPrice ?? latestCandle?.open);
            push('high', meta.dayHigh ?? latestCandle?.high);
            push('low', meta.dayLow ?? latestCandle?.low);
            push('volume', meta.volume ?? latestCandle?.volume, formatVolume);
            break;
        default:
            push('open', meta.openPrice ?? latestCandle?.open);
            push('high', meta.dayHigh ?? latestCandle?.high);
            push('low', meta.dayLow ?? latestCandle?.low);
            push('volume', meta.volume ?? latestCandle?.volume, formatVolume);
    }
    return rows;
}

function SingleAssetSectionImpl({ data, config }) {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const setOrigin = useNavigationStore((s) => s.setOrigin);
    const { format: money } = useMoney();
    const ref = useRef(null);
    const chartHostRef = useRef(null);
    const chartInstanceRef = useRef(null);
    const [initialSize, setInitialSize] = useState(null);
    const inView = useInView(ref, { margin: '200px 0px', once: true });
    const asset = data?.asset;

    const showChart = config?.showChart !== false;
    const chartType = config?.chartType === 'candle' ? 'candle' : 'line';
    const range = RANGES.includes(config?.range) ? config.range : '1M';
    const isDark = typeof document !== 'undefined' && document.documentElement.dataset.theme !== 'light';

    const { data: history } = useQuery({
        queryKey: ['singleAssetHistory', asset?.type, asset?.code, range],
        queryFn: () => fetchAssetHistory(asset.type, asset.code, range),
        enabled: inView && !!asset?.code && !!asset?.type && showChart,
        staleTime: STALE.MEDIUM,
        retry: false,
    });

    const accent = asset ? (ASSET_TYPE_COLORS[asset.type] || '#6366f1') : '#6366f1';
    const cls = getChangeClass(asset?.changePercent);
    const isUp = (asset?.changePercent ?? 0) > 0;
    const priceCcy = priceCurrencyOf(asset);

    const latestCandle = useMemo(() => {
        if (!Array.isArray(history) || history.length === 0) return null;
        const last = history[history.length - 1];
        return last && typeof last === 'object' ? last : null;
    }, [history]);

    const ohlc = useMemo(
        () => (asset ? buildMetadataRows(asset, latestCandle, t, money, priceCcy) : []),
        [asset, latestCandle, t, money, priceCcy],
    );

    const tooltipLabels = useMemo(() => ({
        open: t('singleAssetSection.open'),
        close: t('singleAssetSection.close', { defaultValue: 'Kapanış' }),
        high: t('singleAssetSection.high'),
        low: t('singleAssetSection.low'),
    }), [t]);

    const chartOption = useMemo(
        () => (history ? buildChartOption(history, chartType, accent, isDark, tooltipLabels) : null),
        [history, chartType, accent, isDark, tooltipLabels],
    );

    useEffect(() => {
        if (!inView || initialSize) return undefined;
        const host = chartHostRef.current;
        if (!host) return undefined;
        const measure = () => {
            const w = host.clientWidth;
            const h = host.clientHeight;
            if (w > 0 && h > 0) {
                setInitialSize({ width: w, height: h });
                return true;
            }
            return false;
        };
        if (measure()) return undefined;
        if (typeof ResizeObserver === 'undefined') return undefined;
        const observer = new ResizeObserver(() => {
            if (measure()) observer.disconnect();
        });
        observer.observe(host);
        return () => observer.disconnect();
    }, [inView, initialSize]);

    useEffect(() => {
        if (!initialSize || typeof ResizeObserver === 'undefined') return undefined;
        const host = chartHostRef.current;
        if (!host) return undefined;
        let observer = null;
        const armTimer = window.setTimeout(() => {
            observer = new ResizeObserver(() => {
                const instance = chartInstanceRef.current;
                if (!instance || instance.isDisposed?.()) return;
                const w = host.clientWidth;
                const h = host.clientHeight;
                if (w <= 0 || h <= 0) return;
                if (instance.getWidth() === w && instance.getHeight() === h) return;
                instance.resize({ width: w, height: h });
            });
            observer.observe(host);
        }, 1600);
        return () => {
            window.clearTimeout(armTimer);
            if (observer) observer.disconnect();
        };
    }, [initialSize]);

    const handleChartReady = (instance) => {
        chartInstanceRef.current = instance;
    };

    const handleClick = () => {
        if (!asset) return;
        setOrigin('/market', window.scrollY);
        navigate(`${TYPE_ROUTES[asset.type] ?? '/market'}/${asset.code}`, { state: { from: '/market' } });
    };

    if (!asset) {
        return (
            <Card ref={ref} as="section" radius="xl" padding="md" className="h-full flex items-center justify-center text-fg-subtle text-xs">
                {t('singleAssetSection.empty')}
            </Card>
        );
    }

    return (
        <Card
            ref={ref}
            as="section"
            radius="xl"
            padding="none"
            accentBar={accent}
            className="group h-full relative overflow-hidden"
            style={{ containerType: 'inline-size' }}
        >
            <div
                className="relative h-full flex flex-col"
                style={{
                    fontSize: 'clamp(0.7rem, 1.55cqi, 1.65rem)',
                    padding: '1em',
                    gap: '0.85em',
                }}
            >
                <motion.header
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                    className="flex items-start justify-between cursor-pointer"
                    style={{ gap: '0.95em' }}
                    onClick={handleClick}
                >
                    <div className="flex items-center min-w-0" style={{ gap: '0.7em' }}>
                        {asset.image
                            ? (/^https?:\/\//i.test(asset.image)
                                ? <img
                                    src={asset.image}
                                    alt=""
                                    loading="lazy"
                                    className="rounded-full ring-1 ring-border-default shrink-0"
                                    style={{ width: '2.6em', height: '2.6em' }}
                                />
                                : <span
                                    className="rounded-full shrink-0 flex items-center justify-center leading-none"
                                    style={{ width: '2.6em', height: '2.6em', fontSize: '1.5em' }}
                                >
                                    {asset.image}
                                </span>)
                            : <span
                                className="rounded-full shrink-0 flex items-center justify-center font-bold text-white"
                                style={{ width: '2.6em', height: '2.6em', fontSize: '0.72em', background: accent }}
                            >
                                {(asset.code || '').replace('.IS', '').slice(0, 2)}
                              </span>}
                        <div className="min-w-0">
                            <h3
                                className="font-display font-bold text-fg truncate group-hover:text-accent transition-colors"
                                style={{ fontSize: '1.6em', lineHeight: '1.05', letterSpacing: '-0.01em' }}
                            >
                                {(asset.code || '').replace('.IS', '')}
                            </h3>
                            <p
                                className="font-mono uppercase text-fg-subtle truncate"
                                style={{ fontSize: '0.65em', letterSpacing: '0.22em', marginTop: '0.25em' }}
                            >
                                {asset.name || asset.type}
                            </p>
                        </div>
                    </div>
                    <div className="text-right shrink-0 min-w-0">
                        <p
                            className="font-mono font-bold tabular-nums text-fg truncate"
                            style={{ fontSize: '1.95em', lineHeight: '1' }}
                        >
                            {money(asset.price, priceCcy)}
                        </p>
                        {asset.changePercent != null && (
                            <div
                                className={`inline-flex items-center rounded-md font-mono font-semibold tabular-nums ring-1 ring-inset ring-current/15 ${changeBg[cls]} ${changeColors[cls]}`}
                                style={{
                                    marginTop: '0.4em',
                                    gap: '0.25em',
                                    paddingInline: '0.55em',
                                    paddingBlock: '0.2em',
                                    fontSize: '0.72em',
                                }}
                            >
                                {isUp ? <TrendingUp style={{ width: '1em', height: '1em' }} /> : <TrendingDown style={{ width: '1em', height: '1em' }} />}
                                {formatPercent(asset.changePercent)}
                            </div>
                        )}
                    </div>
                </motion.header>

                {showChart && (
                    <div ref={chartHostRef} className="flex-1 min-h-0 relative">
                        {!inView ? (
                            <div className="absolute inset-0 flex items-center justify-center font-mono uppercase text-fg-subtle" style={{ fontSize: '0.7em', letterSpacing: '0.2em' }}>
                                {t('singleAssetSection.scrollToLoad')}
                            </div>
                        ) : !chartOption || !initialSize ? (
                            <div className="absolute inset-0 rounded-lg overflow-hidden skeleton-sweep" aria-busy="true" aria-label={t('singleAssetSection.loading')} />
                        ) : (
                            <ReactECharts
                                key={`${chartType}-${range}-${asset.type}-${asset.code}`}
                                option={chartOption}
                                style={{ height: '100%', width: '100%' }}
                                opts={{ renderer: 'canvas', width: initialSize.width, height: initialSize.height }}
                                notMerge
                                autoResize={false}
                                onChartReady={handleChartReady}
                            />
                        )}
                    </div>
                )}

                {ohlc.length > 0 && (
                    <footer
                        className={`border-t border-border-default/40 ${!showChart ? 'flex-1 content-center' : ''}`}
                        style={{ paddingTop: '0.75em' }}
                    >
                        <div
                            className="flex flex-wrap items-baseline"
                            style={{ columnGap: '1.5em', rowGap: '0.55em', justifyContent: 'space-around' }}
                        >
                            {ohlc.map((row) => (
                                <div
                                    key={row.label}
                                    className="flex items-baseline min-w-0"
                                    style={{ gap: '0.55em', flex: '0 1 auto' }}
                                >
                                    <span
                                        className="font-mono uppercase text-fg-subtle shrink-0"
                                        style={{ fontSize: '0.6em', letterSpacing: '0.2em' }}
                                    >
                                        {row.label}
                                    </span>
                                    <span
                                        className="font-mono font-semibold tabular-nums text-fg truncate"
                                        style={{ fontSize: '0.92em' }}
                                    >
                                        {row.value}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </footer>
                )}
            </div>
        </Card>
    );
}

export default memo(SingleAssetSectionImpl);
