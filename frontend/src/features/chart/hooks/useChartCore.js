import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { createChart, CandlestickSeries, LineSeries, LineStyle, createSeriesMarkers } from 'lightweight-charts';
import { calculateSMA, calculateEMA } from '../lib/indicators';
import { getChartOptions } from '../lib/chartOptions';
import { priceDecimals, visibleDecimals } from '../../../shared/utils/formatters';
import {
    COMPARE_COLORS,
    analyzeTrend,
    chartTimeEqual,
    chartTimeKey,
    dimColor,
    toChartTime,
    toEpochSec,
} from '../lib/chartCoreHelpers';

const useChartCore = ({ data, symbol, chartType, isDark, indicators, renderDrawingsRef, assetType, compareDatas = [], timeRange, showSecondaryLines = true }) => {
    const hasCompare = Array.isArray(compareDatas) && compareDatas.length > 0;
    const { t, i18n } = useTranslation();
    const chartContainerRef = useRef(null);
    const chartRef = useRef(null);
    const candleSeriesRef = useRef(null);
    const lineSeriesRef = useRef(null);
    const indicatorSeriesRef = useRef({});
    const candleDataRef = useRef([]);
    const volumeDataRef = useRef([]);
    const overlayMetaRef = useRef(new Map());
    const hoveredOverlayRef = useRef(null);
    // A clicked day stays "pinned" (its data survives the cursor leaving) until clicked again or the chart rebuilds.
    const pinnedRef = useRef(null);
    // Set inside the chart effect; lets the toolbar date-picker jump the selected day to a chosen date.
    const selectDateRef = useRef(null);
    // A horizontal price line marking the SELECTED (pinned) day's price; redrawn on pin/select, removed on reset.
    const selectedPriceLineRef = useRef(null);
    const selectedMarkersRef = useRef(null);
    const applyPinLineRef = useRef(null);
    const trend = useMemo(() => {
        if (!data?.candles?.length) return null;
        const candleData = data.candles.map(c => ({
            close: Number(c.close ?? c.price ?? 0),
        }));
        return analyzeTrend(candleData);
    }, [data]);
    const [crosshairData, setCrosshairData] = useState(null);
    // Last value of each overlay (SMA/EMA) by indicator id — shown in the toolbar legend when idle (the
    // built-in right-axis last-value tags are disabled to stop EMA/SMA/price boxes stacking on the axis).
    const [overlayLast, setOverlayLast] = useState({});
    const compareDataAtKeyRef = useRef({ byKey: new Map(), symbols: [] });

    useEffect(() => {
        if (!chartContainerRef.current || !data?.candles?.length) return;
        const scrollY = window.scrollY;
        const accentColor = isDark ? '#5E6AD2' : '#4338ca';
        const bulletinColor = isDark ? '#a855f7' : '#7e22ce';
        if (chartRef.current) { chartRef.current.remove(); chartRef.current = null; }
        indicatorSeriesRef.current = {};
        overlayMetaRef.current.clear();
        hoveredOverlayRef.current = null;
        pinnedRef.current = null;
        selectedPriceLineRef.current = null;
        selectedMarkersRef.current = null;
        // Clear stale hover when the chart rebuilds (asset/range/currency change); this also nulls the host
        // page's lifted hover via onHover, so the analysis card and legend never show the prior dataset's day.
        setCrosshairData(null);
        const chart = createChart(chartContainerRef.current, {
            ...getChartOptions(isDark),
            // autoSize is forced OFF here (getChartOptions sets it true): with autoSize, lightweight-charts
            // OWNS sizing via its own ResizeObserver and IGNORES applyOptions(width/height) — so our manual
            // handleResize was a no-op and, after a fullscreen exit, the canvas stayed stuck at the fullscreen
            // height (a canvas==container / container==canvas-content deadlock autoSize never re-measured).
            // With it off, handleResize (ResizeObserver + the fullscreen resize nudge) controls the size and
            // the chart shrinks back correctly. Mirrors the sub-charts, which already use autoSize:false.
            autoSize: false,
            width: chartContainerRef.current.clientWidth || 300,
            height: chartContainerRef.current.clientHeight || 400,
        });
        chartRef.current = chart;
        const candleData = data.candles.map(c => {
            const close = Number(c.close ?? c.price ?? 0);
            return {
                time: toChartTime(c.candleDate || c.date),
                open: Number(c.open ?? close),
                high: Number(c.high ?? close),
                low: Number(c.low ?? close),
                close,
                sellingPrice: c.sellingPrice != null ? Number(c.sellingPrice) : null,
                buyingPrice: c.buyingPrice != null ? Number(c.buyingPrice) : null,
                effectiveBuyingPrice: c.effectiveBuyingPrice != null ? Number(c.effectiveBuyingPrice) : null,
                effectiveSellingPrice: c.effectiveSellingPrice != null ? Number(c.effectiveSellingPrice) : null,
                bulletinPrice: c.bulletinPrice != null ? Number(c.bulletinPrice) : null,
            };
        });
        candleDataRef.current = candleData;
        // Dynamic priceFormat so sub-cent values (TRY pre-redenomination, cheap crypto)
        // don't get rounded to 0 by Lightweight Charts default precision: 2.
        const priceSamples = candleData.flatMap(c => [c.open, c.high, c.low, c.close,
            c.sellingPrice, c.buyingPrice, c.bulletinPrice].filter(v => v != null && v > 0));
        const minPrice = priceSamples.length ? Math.min(...priceSamples) : 1;
        const pricePrec = priceDecimals(minPrice);
        const priceFormat = { type: 'price', precision: pricePrec, minMove: 10 ** -pricePrec };
        if (chartType === 'candle') {
            const candleSeries = chart.addSeries(CandlestickSeries, {
                upColor: '#26a69a', downColor: '#ef5350',
                borderUpColor: '#26a69a', borderDownColor: '#ef5350',
                wickUpColor: '#26a69a', wickDownColor: '#ef5350',
                priceFormat,
            });
            candleSeriesRef.current = candleSeries;
            lineSeriesRef.current = null;
            candleSeries.setData(candleData);
        } else if (assetType === 'FUND') {
            const priceLine = chart.addSeries(LineSeries, {
                color: accentColor,
                lineWidth: 2,
                crosshairMarkerVisible: true,
                crosshairMarkerRadius: 4,
                crosshairMarkerBorderWidth: 1.5,
                crosshairMarkerBorderColor: accentColor,
                crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                lastValueVisible: true,
                priceLineVisible: true,
                title: t('chart.legend.price'),
                priceFormat,
            });
            priceLine.setData(candleData.map(c => ({ time: c.time, value: c.close })));
            if (showSecondaryLines && !hasCompare && candleData.some(c => c.bulletinPrice != null)) {
                const bulletinLine = chart.addSeries(LineSeries, {
                    color: bulletinColor,
                    lineWidth: 1.5,
                    lineStyle: 2,
                    crosshairMarkerVisible: true,
                    crosshairMarkerRadius: 3,
                    crosshairMarkerBorderColor: bulletinColor,
                    crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                    lastValueVisible: true,
                    priceLineVisible: false,
                    title: t('chart.legend.bulletin'),
                });
                bulletinLine.setData(candleData
                    .filter(c => c.bulletinPrice != null)
                    .map(c => ({ time: c.time, value: c.bulletinPrice })));
            }
            lineSeriesRef.current = priceLine;
            candleSeriesRef.current = priceLine;
        } else if (assetType === 'FOREX') {
            const sellLine = chart.addSeries(LineSeries, {
                color: '#10b981',
                lineWidth: 2,
                crosshairMarkerVisible: true,
                crosshairMarkerRadius: 4,
                crosshairMarkerBorderColor: '#10b981',
                crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                lastValueVisible: true,
                priceLineVisible: true,
                title: t('chart.legend.sell'),
                priceFormat,
            });
            sellLine.setData(candleData
                .filter(c => c.sellingPrice != null)
                .map(c => ({ time: c.time, value: c.sellingPrice })));
            if (showSecondaryLines && !hasCompare && candleData.some(c => c.buyingPrice != null)) {
                const buyLine = chart.addSeries(LineSeries, {
                    color: '#ef4444',
                    lineWidth: 1.5,
                    lineStyle: 2,
                    crosshairMarkerVisible: true,
                    crosshairMarkerRadius: 3,
                    crosshairMarkerBorderColor: '#ef4444',
                    crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                    lastValueVisible: true,
                    priceLineVisible: false,
                    title: t('chart.legend.buy'),
                });
                buyLine.setData(candleData
                    .filter(c => c.buyingPrice != null)
                    .map(c => ({ time: c.time, value: c.buyingPrice })));
            }
            lineSeriesRef.current = sellLine;
            candleSeriesRef.current = sellLine;
        } else {
            const lineSeries = chart.addSeries(LineSeries, {
                color: accentColor,
                lineWidth: 2,
                crosshairMarkerVisible: true,
                crosshairMarkerRadius: 4,
                crosshairMarkerBorderWidth: 1.5,
                crosshairMarkerBorderColor: accentColor,
                crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                lastValueVisible: true,
                priceLineVisible: true,
                priceFormat,
            });
            lineSeriesRef.current = lineSeries;
            candleSeriesRef.current = lineSeries;
            lineSeries.setData(candleData.map(c => ({ time: c.time, value: c.close })));
        }
        volumeDataRef.current = data.candles
            .filter(c => c.volume != null && c.volume > 0)
            .map(c => ({
                time: toChartTime(c.candleDate || c.date),
                value: c.volume,
                color: c.close >= c.open ? 'rgba(38, 166, 154, 0.7)' : 'rgba(239, 83, 80, 0.7)',
            }));
        chart.timeScale().fitContent();
        requestAnimationFrame(() => {
            window.scrollTo(0, scrollY);
            try { chart.timeScale().fitContent(); } catch { void 0; }
        });
        // Builds the readout for a crosshair param (hover OR click); returns null off-data.
        const buildCrosshairData = (param) => {
            if (!param || !param.time) return null;
            const cdIndex = candleDataRef.current.findIndex(c => chartTimeEqual(c.time, param.time));
            const cd = cdIndex >= 0 ? candleDataRef.current[cdIndex] : null;
            if (!cd) return null;
            const compareInfo = compareDataAtKeyRef.current;
            const compares = [];
            if (compareInfo.symbols.length > 0) {
                const k = chartTimeKey(param.time);
                for (const { symbol, color } of compareInfo.symbols) {
                    const byKey = compareInfo.byKey.get(symbol);
                    const pt = byKey?.get(k);
                    if (pt) compares.push({ symbol, value: pt.value, color });
                }
            }
            const overlayVals = {};
            for (const [series, meta] of overlayMetaRef.current) {
                const sd = param.seriesData?.get(series);
                const v = sd?.value ?? sd?.close;
                if (v != null && meta.id) overlayVals[meta.id] = v;
            }
            const prevClose = cdIndex > 0 ? candleDataRef.current[cdIndex - 1].close : null;
            const changeValue = (cd.close != null && prevClose != null) ? Number(cd.close) - Number(prevClose) : null;
            const changePercent = (changeValue != null && Number(prevClose) !== 0) ? (changeValue / Number(prevClose)) * 100 : null;
            return {
                open: cd.open, high: cd.high, low: cd.low, close: cd.close,
                sellingPrice: cd.sellingPrice, buyingPrice: cd.buyingPrice,
                effectiveBuyingPrice: cd.effectiveBuyingPrice, effectiveSellingPrice: cd.effectiveSellingPrice,
                bulletinPrice: cd.bulletinPrice,
                time: cd.time,
                index: cdIndex,
                changeValue, changePercent,
                compares: compares.length > 0 ? compares : undefined,
                overlays: Object.keys(overlayVals).length > 0 ? overlayVals : undefined,
            };
        };
        // Draws a horizontal price line at the pinned day's price (a second reference line alongside the live
        // last-price line) so a selected day — clicked OR chosen from the calendar — is marked on the price
        // axis. createPriceLine is persistent (unlike the mouse-driven crosshair); removed when unpinned/reset.
        const applyPinnedPriceLine = () => {
            const series = candleSeriesRef.current;
            if (!series) return;
            if (selectedPriceLineRef.current) {
                try { series.removePriceLine(selectedPriceLineRef.current); } catch { void 0; }
                selectedPriceLineRef.current = null;
            }
            const pin = pinnedRef.current;
            if (pin && pin.close != null) {
                try {
                    selectedPriceLineRef.current = series.createPriceLine({
                        price: Number(pin.close),
                        color: '#a78bfa',
                        lineWidth: 2,
                        lineStyle: LineStyle.Dashed,
                        axisLabelVisible: true,
                    });
                } catch { void 0; }
            }
            // A dot exactly on the selected point (date × price), persistent like the price line.
            try {
                if (!selectedMarkersRef.current) selectedMarkersRef.current = createSeriesMarkers(series, []);
                selectedMarkersRef.current.setMarkers(pin && pin.time != null
                    ? [{ time: pin.time, position: 'inBar', color: '#a78bfa', shape: 'circle', size: 1.5 }]
                    : []);
            } catch { void 0; }
        };
        applyPinLineRef.current = applyPinnedPriceLine;
        // Click pins a day: its readout survives the cursor leaving instead of snapping back to the latest.
        // Clicking the same day again unpins. Hovering still shows the live day; leaving restores the pin.
        const handleChartClick = (param) => {
            const built = buildCrosshairData(param);
            if (!built) return;
            pinnedRef.current = (pinnedRef.current && pinnedRef.current.index === built.index) ? null : built;
            setCrosshairData(pinnedRef.current || built);
            applyPinnedPriceLine();
        };
        const handleCrosshairMove = (param) => {
            if (renderDrawingsRef.current) renderDrawingsRef.current();
            if (!param || !param.time) {
                setCrosshairData(pinnedRef.current);
                if (hoveredOverlayRef.current) {
                    for (const [s, m] of overlayMetaRef.current) {
                        try { s.applyOptions({ color: m.color, lineWidth: 2 }); } catch { void 0; }
                    }
                    hoveredOverlayRef.current = null;
                }
                return;
            }
            const built = buildCrosshairData(param);
            if (built) setCrosshairData(built);
            const overlays = overlayMetaRef.current;
            if (overlays.size > 0 && param.point) {
                const mouseY = param.point.y;
                let closest = null, closestDist = Infinity;
                for (const [series] of overlays) {
                    const sd = param.seriesData?.get(series);
                    if (!sd) continue;
                    const val = sd.value ?? sd.close;
                    if (val == null) continue;
                    try {
                        const y = series.priceToCoordinate(val);
                        if (y == null) continue;
                        const dist = Math.abs(mouseY - y);
                        if (dist < closestDist) { closestDist = dist; closest = series; }
                    } catch { void 0; }
                }
                const hovered = closestDist < 20 ? closest : null;
                if (hovered !== hoveredOverlayRef.current) {
                    hoveredOverlayRef.current = hovered;
                    for (const [s, m] of overlays) {
                        if (!hovered) {
                            s.applyOptions({ color: m.color, lineWidth: 2 });
                        } else if (s === hovered) {
                            s.applyOptions({ color: m.color, lineWidth: 3 });
                        } else {
                            s.applyOptions({ color: dimColor(m.color), lineWidth: 2 });
                        }
                    }
                }
            }
        };
        const handleUpdate = () => { if (renderDrawingsRef.current) renderDrawingsRef.current(); };
        chart.timeScale().subscribeVisibleTimeRangeChange(handleUpdate);
        chart.timeScale().subscribeVisibleLogicalRangeChange(handleUpdate);
        chart.subscribeCrosshairMove(handleCrosshairMove);
        chart.subscribeClick(handleChartClick);
        // Date picker: pick a calendar date → pin the nearest candle so the LENS/analytics panel/legend show
        // that day and a horizontal price line marks it. Nearest-by-date so weekends/holidays still resolve.
        selectDateRef.current = (isoDate) => {
            const cdArr = candleDataRef.current;
            if (!cdArr.length || !isoDate) return;
            const parts = String(isoDate).slice(0, 10).split('-').map(Number);
            if (parts.length < 3 || parts.some(Number.isNaN)) return;
            const targetEpoch = Math.floor(Date.UTC(parts[0], parts[1] - 1, parts[2]) / 1000);
            let bestIdx = -1;
            let bestDist = Infinity;
            for (let i = 0; i < cdArr.length; i++) {
                const e = toEpochSec(cdArr[i].time);
                if (e == null) continue;
                const dist = Math.abs(e - targetEpoch);
                if (dist < bestDist) { bestDist = dist; bestIdx = i; }
            }
            if (bestIdx < 0) return;
            const c = cdArr[bestIdx];
            const built = buildCrosshairData({ time: c.time });
            if (built) { pinnedRef.current = built; setCrosshairData(built); }
            applyPinnedPriceLine();
        };
        const handleResize = () => {
            if (chartContainerRef.current && chart) {
                chart.applyOptions({
                    width: chartContainerRef.current.clientWidth,
                    height: chartContainerRef.current.clientHeight || 500,
                });
                // Repack candles into the new width so mobile container growth (e.g. rotation,
                // fullscreen toggle, soft-keyboard close) re-expands plot content, not just canvas.
                try { chart.timeScale().fitContent(); } catch { void 0; }
                if (renderDrawingsRef.current) renderDrawingsRef.current();
            }
        };
        const resizeObserver = new ResizeObserver(handleResize);
        resizeObserver.observe(chartContainerRef.current);
        window.addEventListener('resize', handleResize);
        // Safari mobile fires orientationchange before the layout/visual viewport settle, so resize again on a
        // delay to pick up the final container size (else the chart stays locked at its pre-rotation dimensions).
        const handleOrientation = () => { handleResize(); setTimeout(handleResize, 250); };
        window.addEventListener('orientationchange', handleOrientation);
        return () => {
            window.removeEventListener('resize', handleResize);
            window.removeEventListener('orientationchange', handleOrientation);
            resizeObserver.disconnect();
            try {
                chart.timeScale().unsubscribeVisibleTimeRangeChange(handleUpdate);
                chart.timeScale().unsubscribeVisibleLogicalRangeChange(handleUpdate);
                chart.unsubscribeCrosshairMove(handleCrosshairMove);
                chart.unsubscribeClick(handleChartClick);
            } catch { void 0; }
            if (chartRef.current) { chartRef.current.remove(); chartRef.current = null; }
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- imperative lightweight-charts create/subscribe/destroy; isDark and refs omitted on purpose so a separate [isDark] effect retints without tearing down the chart
    }, [data, symbol, chartType, timeRange, i18n.language, showSecondaryLines, hasCompare]);

    useEffect(() => {
        if (chartRef.current) {
            chartRef.current.applyOptions(getChartOptions(isDark));
            if (lineSeriesRef.current) {
                lineSeriesRef.current.applyOptions({
                    crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                });
            }
        }
    }, [isDark]);


    const compareSeriesRefs = useRef([]);
    const mainPercentSeriesRef = useRef(null);
    const zeroLineSeriesRef = useRef(null);

    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;

        compareSeriesRefs.current.forEach(s => {
            try { chart.removeSeries(s); } catch { void 0; }
        });
        compareSeriesRefs.current = [];
        if (mainPercentSeriesRef.current) {
            try { chart.removeSeries(mainPercentSeriesRef.current); } catch { void 0; }
            mainPercentSeriesRef.current = null;
        }
        if (zeroLineSeriesRef.current) {
            try { chart.removeSeries(zeroLineSeriesRef.current); } catch { void 0; }
            zeroLineSeriesRef.current = null;
        }

        const mainSeries = candleSeriesRef.current;
        if (!hasCompare) {
            if (mainSeries) {
                try { mainSeries.applyOptions({ visible: true }); } catch { void 0; }
            }
            try { chart.priceScale('left').applyOptions({ visible: false }); } catch { void 0; }
            try { chart.priceScale('right').applyOptions({ mode: 0 }); } catch { void 0; }
            return;
        }

        const candleData = candleDataRef.current;
        if (!candleData.length) return;

        const keyOf = chartTimeKey;

        const mainPoints = candleData.map(c => ({ time: c.time, value: Number(c.close), key: keyOf(c.time), epoch: toEpochSec(c.time) }));
        const validCompares = compareDatas
            .filter(cd => cd.data?.candles?.length)
            .map((cd, idx) => {
                const points = cd.data.candles.map(c => {
                    const t = toChartTime(c.candleDate || c.date);
                    return { time: t, value: Number(c.close ?? c.price ?? 0), key: keyOf(t), epoch: toEpochSec(t) };
                });
                return { symbol: cd.symbol, points, byKey: new Map(points.map(p => [p.key, p])), color: COMPARE_COLORS[idx % COMPARE_COLORS.length] };
            });
        if (validCompares.length === 0) {
            compareDataAtKeyRef.current = { byKey: new Map(), symbols: [] };
            if (mainSeries) try { mainSeries.applyOptions({ visible: true }); } catch { void 0; }
            return;
        }
        compareDataAtKeyRef.current = {
            byKey: new Map(validCompares.map(c => [c.symbol, c.byKey])),
            symbols: validCompares.map(c => ({ symbol: c.symbol, color: c.color })),
        };

        const MIN_SANE_BASELINE = 0.01;
        const commonPoints = mainPoints.filter(p =>
            validCompares.every(cmp => cmp.byKey.has(p.key))
        );
        if (commonPoints.length === 0) {
            if (mainSeries) try { mainSeries.applyOptions({ visible: true }); } catch { void 0; }
            return;
        }

        let baseline = null;
        for (const m of commonPoints) {
            if (m.value < MIN_SANE_BASELINE) continue;
            const compareBases = validCompares.map(cmp => cmp.byKey.get(m.key));
            if (compareBases.every(cb => cb && cb.value >= MIN_SANE_BASELINE)) {
                baseline = { startEpoch: m.epoch, mainBase: m.value, compareBases };
                break;
            }
        }
        if (!baseline) {
            if (mainSeries) try { mainSeries.applyOptions({ visible: true }); } catch { void 0; }
            return;
        }

        if (mainSeries) {
            try { mainSeries.applyOptions({ visible: false }); } catch { void 0; }
        }

        const fmtPercent = (p) => (p >= 0 ? '+' : '') + p.toFixed(visibleDecimals(p, 2)) + '%';
        const percentFormat = { type: 'custom', minMove: 0.01, formatter: fmtPercent };

        const { mainBase, startEpoch } = baseline;
        const inRange = (p) => !startEpoch || !p.epoch || p.epoch >= startEpoch;
        const mainPercentData = mainPoints
            .filter(inRange)
            .map(p => ({ time: p.time, value: ((p.value - mainBase) / mainBase) * 100 }));

        const mainPercentSeries = chart.addSeries(LineSeries, {
            color: isDark ? '#5E6AD2' : '#4338ca',
            lineWidth: 2.5,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 4,
            crosshairMarkerBorderColor: isDark ? '#5E6AD2' : '#4338ca',
            crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
            lastValueVisible: true,
            priceLineVisible: true,
            priceLineColor: '#5E6AD230',
            priceLineStyle: 2,
            title: symbol?.toUpperCase() || '',
            priceScaleId: 'right',
            priceFormat: percentFormat,
        });
        mainPercentSeries.setData(mainPercentData);
        mainPercentSeriesRef.current = mainPercentSeries;

        validCompares.forEach((cmp, idx) => {
            const compareBase = baseline.compareBases[idx].value;
            const series = chart.addSeries(LineSeries, {
                color: cmp.color,
                lineWidth: 2,
                crosshairMarkerVisible: true,
                crosshairMarkerRadius: 3,
                crosshairMarkerBorderColor: cmp.color,
                crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                lastValueVisible: true,
                priceLineVisible: true,
                priceLineColor: cmp.color + '30',
                priceLineStyle: 2,
                title: cmp.symbol.toUpperCase(),
                priceScaleId: 'right',
                priceFormat: percentFormat,
            });
            series.setData(cmp.points
                .filter(inRange)
                .map(p => ({ time: p.time, value: ((p.value - compareBase) / compareBase) * 100 })));
            compareSeriesRefs.current.push(series);
        });

        const zeroLine = chart.addSeries(LineSeries, {
            color: isDark ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.12)',
            lineWidth: 1,
            lineStyle: 2,
            crosshairMarkerVisible: false,
            lastValueVisible: false,
            priceLineVisible: false,
            priceScaleId: 'right',
        });
        const firstTime = mainPercentData[0]?.time;
        const lastTime = mainPercentData[mainPercentData.length - 1]?.time;
        if (firstTime && lastTime) {
            zeroLine.setData([{ time: firstTime, value: 0 }, { time: lastTime, value: 0 }]);
        }
        zeroLineSeriesRef.current = zeroLine;

        chart.priceScale('right').applyOptions({
            visible: true,
            scaleMargins: { top: 0.08, bottom: 0.08 },
            autoScale: true,
        });

        if (firstTime && lastTime) {
            try { chart.timeScale().setVisibleRange({ from: firstTime, to: lastTime }); }
            catch { chart.timeScale().fitContent(); }
        } else {
            chart.timeScale().fitContent();
        }

        return () => {
            compareSeriesRefs.current.forEach(s => {
                if (chartRef.current) {
                    try { chartRef.current.removeSeries(s); } catch { void 0; }
                }
            });
            compareSeriesRefs.current = [];
            if (mainPercentSeriesRef.current && chartRef.current) {
                try { chartRef.current.removeSeries(mainPercentSeriesRef.current); } catch { void 0; }
                mainPercentSeriesRef.current = null;
            }
            if (zeroLineSeriesRef.current && chartRef.current) {
                try { chartRef.current.removeSeries(zeroLineSeriesRef.current); } catch { void 0; }
                zeroLineSeriesRef.current = null;
            }
            if (mainSeries) {
                try { mainSeries.applyOptions({ visible: true }); } catch { void 0; }
            }
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps -- compare-series lifecycle keyed by a serialized symbol:length string to avoid array-identity rebuilds; compareDatas intentionally not a raw dep
    }, [compareDatas.map(c => `${c.symbol}:${c.data?.candles?.length ?? 0}`).join('|'), hasCompare, data, isDark, symbol, chartType, showSecondaryLines]);

    useEffect(() => {
        const chart = chartRef.current;
        const candleData = candleDataRef.current;
        if (!chart || !candleData.length) return;
        const existingIds = new Set(Object.keys(indicatorSeriesRef.current));
        const desiredOverlay = indicators.filter(i => i.type !== 'RSI');
        const desiredIds = new Set(desiredOverlay.map(i => i.id));
        existingIds.forEach(id => {
            if (!desiredIds.has(id)) {
                overlayMetaRef.current.delete(indicatorSeriesRef.current[id]);
                try { chart.removeSeries(indicatorSeriesRef.current[id]); } catch { void 0; }
                delete indicatorSeriesRef.current[id];
            }
        });
        const lastMap = {};
        desiredOverlay.forEach(ind => {
            if (ind.type === 'MACD') return;
            const calcFn = ind.type === 'SMA' ? calculateSMA : calculateEMA;
            const seriesData = calcFn(candleData, ind.period);
            lastMap[ind.id] = seriesData.length ? seriesData[seriesData.length - 1].value : null;
            const meta = { color: ind.color, id: ind.id };
            // lastValueVisible/priceLineVisible false: overlay values live in the toolbar legend (crosshair-
            // aware), not as stacked right-axis tags that overlapped each other and the price.
            if (indicatorSeriesRef.current[ind.id]) {
                const series = indicatorSeriesRef.current[ind.id];
                series.applyOptions({ color: ind.color, visible: ind.visible, title: `${ind.type} ${ind.period}`, lastValueVisible: false, priceLineVisible: false });
                series.setData(seriesData);
                overlayMetaRef.current.set(series, meta);
            } else {
                const series = chart.addSeries(LineSeries, {
                    color: ind.color, lineWidth: 2, title: `${ind.type} ${ind.period}`,
                    crosshairMarkerVisible: false, visible: ind.visible,
                    lastValueVisible: false, priceLineVisible: false,
                });
                series.setData(seriesData);
                indicatorSeriesRef.current[ind.id] = series;
                overlayMetaRef.current.set(series, meta);
            }
        });
        setOverlayLast(lastMap);
    }, [indicators, data, chartType]);

    // Reset the READOUTS to the latest day: drop any pinned/hovered selection so the LENS, the right analytics
    // panel and the on-chart legend all snap back to the newest bar. The chart's own zoom/pan is intentionally
    // left untouched — this only clears the selected-day state, it does not re-fit or re-scroll the chart.
    const resetView = useCallback(() => {
        pinnedRef.current = null;
        hoveredOverlayRef.current = null;
        setCrosshairData(null);
        applyPinLineRef.current?.();
    }, []);

    // Stable wrapper over the effect-scoped date selector so the toolbar date-picker can drive day selection.
    const selectDate = useCallback((isoDate) => selectDateRef.current?.(isoDate), []);

    return { chartRef, chartContainerRef, candleSeriesRef, candleDataRef, volumeDataRef, trend, crosshairData, overlayLast, resetView, selectDate };
};

export default useChartCore;
