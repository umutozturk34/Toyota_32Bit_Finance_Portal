import { useEffect, useRef, useState } from 'react';
import { createChart, CandlestickSeries, LineSeries } from 'lightweight-charts';
import { calculateSMA, calculateEMA } from '../utils/indicators';
import { getChartOptions } from '../utils/chartOptions';

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

const useChartCore = ({ data, symbol, chartType, isDark, indicators, renderDrawingsRef }) => {
    const chartContainerRef = useRef(null);
    const chartRef = useRef(null);
    const candleSeriesRef = useRef(null);
    const lineSeriesRef = useRef(null);
    const indicatorSeriesRef = useRef({});
    const candleDataRef = useRef([]);
    const volumeDataRef = useRef([]);
    const [trend, setTrend] = useState(null);
    const [crosshairData, setCrosshairData] = useState(null);

    useEffect(() => {
        if (!chartContainerRef.current || !data?.candles?.length) return;
        if (chartRef.current) { chartRef.current.remove(); chartRef.current = null; }
        indicatorSeriesRef.current = {};
        const chart = createChart(chartContainerRef.current, {
            ...getChartOptions(isDark),
            width: chartContainerRef.current.clientWidth,
            height: 500,
        });
        chartRef.current = chart;
        const candleData = data.candles.map(c => ({
            time: new Date(c.candleDate || c.date).getTime() / 1000,
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
        volumeDataRef.current = data.candles.map(c => ({
            time: new Date(c.candleDate || c.date).getTime() / 1000,
            value: c.volume || Math.floor(Math.random() * 1000000) + 100000,
            color: c.close >= c.open ? 'rgba(38, 166, 154, 0.7)' : 'rgba(239, 83, 80, 0.7)',
        }));
        setTrend(analyzeTrend(candleData));
        const handleCrosshairMove = (param) => {
            if (renderDrawingsRef.current) renderDrawingsRef.current();
            if (!param || !param.time) { setCrosshairData(null); return; }
            const cd = candleDataRef.current.find(c => c.time === param.time);
            if (cd) setCrosshairData({ open: cd.open, high: cd.high, low: cd.low, close: cd.close, time: cd.time });
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

    useEffect(() => {
        const chart = chartRef.current;
        const candleData = candleDataRef.current;
        if (!chart || !candleData.length) return;
        const existingIds = new Set(Object.keys(indicatorSeriesRef.current));
        const desiredOverlay = indicators.filter(i => i.type !== 'RSI');
        const desiredIds = new Set(desiredOverlay.map(i => i.id));
        existingIds.forEach(id => {
            if (!desiredIds.has(id)) {
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
            } else {
                const series = chart.addSeries(LineSeries, {
                    color: ind.color, lineWidth: 2, title: `${ind.type} ${ind.period}`,
                    crosshairMarkerVisible: false, visible: ind.visible,
                });
                series.setData(seriesData);
                indicatorSeriesRef.current[ind.id] = series;
            }
        });
    }, [indicators, data, chartType]);

    return { chartRef, chartContainerRef, candleSeriesRef, candleDataRef, volumeDataRef, trend, crosshairData };
};

export default useChartCore;
