import { useEffect, useRef, useState } from 'react';
import { createChart, CandlestickSeries, LineSeries } from 'lightweight-charts';
import { calculateSMA, calculateEMA } from './indicators';
import { getChartOptions } from './chartOptions';

const dimColor = (color, alpha = 0.4) => {
    if (!color) return color;
    const hex = color.replace('#', '');
    const r = parseInt(hex.substring(0, 2), 16);
    const g = parseInt(hex.substring(2, 4), 16);
    const b = parseInt(hex.substring(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

const toChartTime = (dateStr) => {
    if (!dateStr) return 0;
    const s = String(dateStr);
    if (s.length >= 10) {
        const [y, m, d] = s.substring(0, 10).split('-').map(Number);
        return { year: y, month: m, day: d };
    }
    return new Date(s).getTime() / 1000;
};

const chartTimeEqual = (a, b) => {
    if (a === b) return true;
    if (a && b && typeof a === 'object' && typeof b === 'object') {
        return a.year === b.year && a.month === b.month && a.day === b.day;
    }
    return false;
};

const analyzeTrend = (d) => {
    if (!d || d.length < 20) return null;
    const recent = d.slice(-20);
    const first = recent.slice(0, 10);
    const second = recent.slice(10);
    const avg1 = first.reduce((s, c) => s + c.close, 0) / first.length;
    const avg2 = second.reduce((s, c) => s + c.close, 0) / second.length;
    const pct = ((avg2 - avg1) / avg1) * 100;
    if (pct > 2) return { direction: 'up', change: pct };
    if (pct > 0.5) return { direction: 'up', change: pct };
    if (pct < -2) return { direction: 'down', change: pct };
    if (pct < -0.5) return { direction: 'down', change: pct };
    return { direction: 'neutral', change: pct };
};

const useChartCore = ({ data, symbol, chartType, isDark, indicators, renderDrawingsRef, assetType, compareData, compareSymbol }) => {
    const chartContainerRef = useRef(null);
    const chartRef = useRef(null);
    const candleSeriesRef = useRef(null);
    const lineSeriesRef = useRef(null);
    const indicatorSeriesRef = useRef({});
    const candleDataRef = useRef([]);
    const volumeDataRef = useRef([]);
    const overlayMetaRef = useRef(new Map());
    const hoveredOverlayRef = useRef(null);
    const [trend, setTrend] = useState(null);
    const [crosshairData, setCrosshairData] = useState(null);

    useEffect(() => {
        if (!chartContainerRef.current || !data?.candles?.length) return;
        if (chartRef.current) { chartRef.current.remove(); chartRef.current = null; }
        indicatorSeriesRef.current = {};
        overlayMetaRef.current.clear();
        hoveredOverlayRef.current = null;
        const chart = createChart(chartContainerRef.current, {
            ...getChartOptions(isDark),
            width: chartContainerRef.current.clientWidth,
            height: 500,
        });
        chartRef.current = chart;
        const candleData = data.candles.map(c => ({
            time: toChartTime(c.candleDate || c.date),
            open: c.open, high: c.high, low: c.low, close: c.close,
        }));
        candleDataRef.current = candleData;
        if (chartType === 'candle') {
            const candleSeries = chart.addSeries(CandlestickSeries, {
                upColor: '#26a69a', downColor: '#ef5350',
                borderUpColor: '#26a69a', borderDownColor: '#ef5350',
                wickUpColor: '#26a69a', wickDownColor: '#ef5350',
            });
            candleSeriesRef.current = candleSeries;
            lineSeriesRef.current = null;
            candleSeries.setData(candleData);
        } else if (assetType === 'FUND') {
            const fundLine = chart.addSeries(LineSeries, {
                color: '#5E6AD2',
                lineWidth: 2,
                crosshairMarkerVisible: true,
                crosshairMarkerRadius: 4,
                crosshairMarkerBorderWidth: 1.5,
                crosshairMarkerBorderColor: '#5E6AD2',
                crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                lastValueVisible: true,
                priceLineVisible: true,
            });
            lineSeriesRef.current = fundLine;
            candleSeriesRef.current = fundLine;
            fundLine.setData(candleData.map(c => ({ time: c.time, value: c.close })));
        } else {
            const lineSeries = chart.addSeries(LineSeries, {
                color: '#5E6AD2',
                lineWidth: 2,
                crosshairMarkerVisible: true,
                crosshairMarkerRadius: 4,
                crosshairMarkerBorderWidth: 1.5,
                crosshairMarkerBorderColor: '#5E6AD2',
                crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
                lastValueVisible: true,
                priceLineVisible: true,
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
        setTrend(analyzeTrend(candleData));
        const handleCrosshairMove = (param) => {
            if (renderDrawingsRef.current) renderDrawingsRef.current();
            if (!param || !param.time) {
                setCrosshairData(null);
                if (hoveredOverlayRef.current) {
                    for (const [s, m] of overlayMetaRef.current) {
                        try { s.applyOptions({ color: m.color, lineWidth: 2 }); } catch { }
                    }
                    hoveredOverlayRef.current = null;
                }
                return;
            }
            const cd = candleDataRef.current.find(c => chartTimeEqual(c.time, param.time));
            if (cd) setCrosshairData({ open: cd.open, high: cd.high, low: cd.low, close: cd.close, time: cd.time });
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
                    } catch { }
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
        const handleResize = () => {
            if (chartContainerRef.current && chart) {
                chart.applyOptions({ width: chartContainerRef.current.clientWidth });
                if (renderDrawingsRef.current) renderDrawingsRef.current();
            }
        };
        window.addEventListener('resize', handleResize);
        return () => {
            window.removeEventListener('resize', handleResize);
            try {
                chart.timeScale().unsubscribeVisibleTimeRangeChange(handleUpdate);
                chart.timeScale().unsubscribeVisibleLogicalRangeChange(handleUpdate);
                chart.unsubscribeCrosshairMove(handleCrosshairMove);
            } catch { }
            if (chartRef.current) { chartRef.current.remove(); chartRef.current = null; }
        };
    }, [data, symbol, chartType]);

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


    const compareSeriesRef = useRef(null);

    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;
        if (compareSeriesRef.current) {
            try { chart.removeSeries(compareSeriesRef.current); } catch {}
            compareSeriesRef.current = null;
        }
        if (!compareData?.candles?.length || !compareSymbol) return;
        const candleData = candleDataRef.current;
        let compareCandles = compareData.candles.map(c => ({
            time: toChartTime(c.candleDate || c.date),
            value: c.close,
        }));
        if (compareCandles.length === 1 && candleData.length > 1) {
            const price = compareCandles[0].value;
            compareCandles = [
                { time: candleData[0].time, value: price },
                { time: candleData[candleData.length - 1].time, value: price },
            ];
        }
        const series = chart.addSeries(LineSeries, {
            color: '#ef4444',
            lineWidth: 2,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 3,
            crosshairMarkerBorderColor: '#ef4444',
            crosshairMarkerBackgroundColor: isDark ? '#050506' : '#ffffff',
            lastValueVisible: true,
            priceLineVisible: true,
            title: compareSymbol.toUpperCase(),
            priceScaleId: 'left',
        });
        series.setData(compareCandles);
        chart.priceScale('left').applyOptions({
            visible: true,
            scaleMargins: { top: 0.05, bottom: 0.05 },
            autoScale: true,
        });
        compareSeriesRef.current = series;
        return () => {
            if (compareSeriesRef.current && chartRef.current) {
                try { chartRef.current.removeSeries(compareSeriesRef.current); } catch {}
                compareSeriesRef.current = null;
                try { chartRef.current.priceScale('left').applyOptions({ visible: false }); } catch {}
            }
        };
    }, [compareData, compareSymbol, data, isDark]);

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
                try { chart.removeSeries(indicatorSeriesRef.current[id]); } catch { }
                delete indicatorSeriesRef.current[id];
            }
        });
        desiredOverlay.forEach(ind => {
            if (ind.type === 'MACD') return;
            const calcFn = ind.type === 'SMA' ? calculateSMA : calculateEMA;
            const seriesData = calcFn(candleData, ind.period);
            if (indicatorSeriesRef.current[ind.id]) {
                const series = indicatorSeriesRef.current[ind.id];
                series.applyOptions({ color: ind.color, visible: ind.visible, title: `${ind.type} ${ind.period}` });
                series.setData(seriesData);
                overlayMetaRef.current.set(series, { color: ind.color });
            } else {
                const series = chart.addSeries(LineSeries, {
                    color: ind.color, lineWidth: 2, title: `${ind.type} ${ind.period}`,
                    crosshairMarkerVisible: false, visible: ind.visible,
                });
                series.setData(seriesData);
                indicatorSeriesRef.current[ind.id] = series;
                overlayMetaRef.current.set(series, { color: ind.color });
            }
        });
    }, [indicators, data, chartType]);

    return { chartRef, chartContainerRef, candleSeriesRef, candleDataRef, volumeDataRef, trend, crosshairData };
};

export default useChartCore;
