import { useCallback, useEffect, useRef, useState } from 'react';
import { createChart, LineSeries, HistogramSeries } from 'lightweight-charts';
import { calculateRSI, calculateMACD } from '../lib/indicators';
import { getChartOptions } from '../lib/chartOptions';

// Pad indicator data to the FULL candle set with whitespace ({time} only) for leading/missing bars, so every
// sub-chart shares the price chart's exact bar indices. The pane sync is index-based (visible LOGICAL range);
// without this, RSI/MACD (which start `period` bars in) and the volume histogram (which drops empty bars) have
// fewer/shifted bars, so the same x-position shows a different date than the price chart above.
const padToCandles = (data, candles) => {
    if (!Array.isArray(candles) || !candles.length) return data;
    const byTime = new Map(data.map((d) => [d.time, d]));
    return candles.map((c) => byTime.get(c.time) ?? { time: c.time });
};

const createSubChart = (container, isDark, height) => {
    const opts = getChartOptions(isDark);
    const chart = createChart(container, {
        ...opts,
        // v5 ignores explicit width/height when autoSize is on, which collapses sub-panes to ~0px in
        // fullscreen (the container has no intrinsic height there) and detaches the volume label. Force
        // the fixed pixel height; width stays responsive via the manual ResizeObserver below.
        autoSize: false,
        width: container.clientWidth,
        height,
        timeScale: { ...opts.timeScale, visible: true },
    });
    const observer = new ResizeObserver(() => {
        try {
            chart.applyOptions({ width: container.clientWidth });
        } catch { /* chart already removed */ }
    });
    observer.observe(container);
    chart.__resizeObserver = observer;
    return chart;
};

const syncTimeScales = (mainChart, subChart) => {
    if (!mainChart) return;
    const syncToSub = () => {
        const r = mainChart.timeScale().getVisibleLogicalRange();
        if (r) subChart.timeScale().setVisibleLogicalRange(r);
    };
    mainChart.timeScale().subscribeVisibleLogicalRangeChange(syncToSub);
    syncToSub();
    subChart.timeScale().subscribeVisibleLogicalRangeChange(range => {
        if (range) mainChart.timeScale().setVisibleLogicalRange(range);
    });
    // The sub->main handler dies when the sub-chart is removed, but syncToSub lives on the persistent MAIN
    // chart — without this teardown it accumulates a new handler on every toggle/resize/fullscreen change
    // (firing on every pan/zoom). cleanupChart calls this before removing the sub-chart.
    subChart.__unsyncMain = () => {
        try { mainChart.timeScale().unsubscribeVisibleLogicalRangeChange(syncToSub); } catch { /* removed */ }
    };
};

const cleanupChart = (chartRef) => {
    if (chartRef.current) {
        if (chartRef.current.__unsyncMain) {
            try { chartRef.current.__unsyncMain(); } catch { /* ignore */ }
        }
        if (chartRef.current.__resizeObserver) {
            try { chartRef.current.__resizeObserver.disconnect(); } catch { /* ignore */ }
        }
        chartRef.current.remove();
        chartRef.current = null;
    }
};

const useSubCharts = ({ chartRef, candleDataRef, volumeDataRef, isDark, hasRSI, rsiIndicator, hasMACD, macdIndicator, showVolume, data, showInvestorCount, showPortfolioSize, isFullscreen = false }) => {
    const indicatorHeight = isFullscreen ? 110 : 150;
    const histogramHeight = isFullscreen ? 95 : 120;
    const rsiChartRef = useRef(null);
    const rsiContainerRef = useRef(null);
    const rsiSeriesRef = useRef(null);
    const macdChartRef = useRef(null);
    const macdContainerRef = useRef(null);
    const volumeChartRef = useRef(null);
    const volumeContainerRef = useRef(null);
    const investorCountChartRef = useRef(null);
    const investorCountContainerRef = useRef(null);
    const portfolioSizeChartRef = useRef(null);
    const portfolioSizeContainerRef = useRef(null);

    // Crosshair-driven sub-panel values. Each sub-chart is a separate lightweight-charts instance, so the
    // MAIN chart's crosshair param never carries their series; instead we cache each series by time and look
    // up the hovered time. last = the final point, shown before any hover and when the crosshair leaves.
    const [subValues, setSubValues] = useState({});
    const rsiLookupRef = useRef({ map: new Map(), last: null });
    const macdLookupRef = useRef({ map: new Map(), last: null });
    const signalLookupRef = useRef({ map: new Map(), last: null });

    // Shared crosshair handler so the values update whether the mouse is over the MAIN chart OR any sub-panel
    // (it is subscribed to all of them). param.time is looked up in each cached series; off-chart falls back
    // to the last point. Stable identity so the same reference can be subscribed/unsubscribed per chart.
    const applyCrosshair = useCallback((param) => {
        const t = param?.time;
        const lookup = (ref) => (t != null ? (ref.current.map.get(t) ?? ref.current.last) : ref.current.last);
        setSubValues((s) => {
            const next = { rsi: lookup(rsiLookupRef), macd: lookup(macdLookupRef), signal: lookup(signalLookupRef) };
            if (s.rsi === next.rsi && s.macd === next.macd && s.signal === next.signal) return s;
            return next;
        });
    }, []);

    useEffect(() => {
        if (!hasRSI || !rsiContainerRef.current || !candleDataRef.current.length) {
            cleanupChart(rsiChartRef);
            return;
        }
        cleanupChart(rsiChartRef);
        const rsiChart = createSubChart(rsiContainerRef.current, isDark, indicatorHeight);
        rsiChartRef.current = rsiChart;
        const period = rsiIndicator?.period || 14;
        const rsiData = calculateRSI(candleDataRef.current, period);
        const rsiSeries = rsiChart.addSeries(LineSeries, {
            color: rsiIndicator?.color || '#e91e63',
            lineWidth: 2,
            title: `RSI ${period}`,
            priceScaleId: 'right',
        });
        rsiSeriesRef.current = rsiSeries;
        rsiSeries.setData(padToCandles(rsiData, candleDataRef.current));
        rsiLookupRef.current = {
            map: new Map(rsiData.map((d) => [d.time, d.value])),
            last: rsiData.length ? rsiData[rsiData.length - 1].value : null,
        };
        setSubValues((s) => ({ ...s, rsi: rsiLookupRef.current.last }));
        rsiChart.priceScale('right').applyOptions({
            scaleMargins: { top: 0.05, bottom: 0.05 },
            autoScale: false,
        });
        syncTimeScales(chartRef.current, rsiChart);
        rsiChart.subscribeCrosshairMove(applyCrosshair); // hovering the RSI pane also updates the values
        return () => cleanupChart(rsiChartRef);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- imperative RSI sub-chart create+cleanup; isDark/height retinted by adjacent effects, candle data read via ref
    }, [hasRSI, rsiIndicator, data, isFullscreen]);

    useEffect(() => {
        if (rsiChartRef.current) rsiChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!hasMACD || !macdContainerRef.current || !candleDataRef.current.length) {
            cleanupChart(macdChartRef);
            return;
        }
        cleanupChart(macdChartRef);
        const macdChart = createSubChart(macdContainerRef.current, isDark, indicatorHeight);
        macdChartRef.current = macdChart;
        const { macd, signal, histogram } = calculateMACD(candleDataRef.current);
        const histSeries = macdChart.addSeries(HistogramSeries, {
            priceFormat: { type: 'price', precision: 4, minMove: 0.0001 },
            priceScaleId: 'right',
        });
        histSeries.setData(padToCandles(histogram, candleDataRef.current));
        const macdColor = macdIndicator?.color || '#06b6d4';
        const macdSeries = macdChart.addSeries(LineSeries, {
            color: macdColor,
            lineWidth: 2,
            title: 'MACD',
            priceScaleId: 'right',
            crosshairMarkerVisible: false,
        });
        macdSeries.setData(padToCandles(macd, candleDataRef.current));
        const signalSeries = macdChart.addSeries(LineSeries, {
            color: '#f59e0b',
            lineWidth: 1,
            title: 'Signal',
            priceScaleId: 'right',
            crosshairMarkerVisible: false,
        });
        signalSeries.setData(padToCandles(signal, candleDataRef.current));
        macdLookupRef.current = {
            map: new Map(macd.map((d) => [d.time, d.value])),
            last: macd.length ? macd[macd.length - 1].value : null,
        };
        signalLookupRef.current = {
            map: new Map(signal.map((d) => [d.time, d.value])),
            last: signal.length ? signal[signal.length - 1].value : null,
        };
        setSubValues((s) => ({ ...s, macd: macdLookupRef.current.last, signal: signalLookupRef.current.last }));
        macdChart.priceScale('right').applyOptions({
            scaleMargins: { top: 0.1, bottom: 0.1 },
        });
        syncTimeScales(chartRef.current, macdChart);
        macdChart.subscribeCrosshairMove(applyCrosshair); // hovering the MACD pane also updates the values
        return () => cleanupChart(macdChartRef);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- imperative MACD sub-chart create+cleanup; isDark/height retinted by adjacent effects, candle data read via ref
    }, [hasMACD, macdIndicator, data, isFullscreen]);

    useEffect(() => {
        if (macdChartRef.current) macdChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!showVolume || !volumeContainerRef.current || !volumeDataRef.current.length) {
            cleanupChart(volumeChartRef);
            return;
        }
        const volChart = createSubChart(volumeContainerRef.current, isDark, histogramHeight);
        volumeChartRef.current = volChart;
        const volSeries = volChart.addSeries(HistogramSeries, { color: '#26a69a', priceFormat: { type: 'volume' } });
        volSeries.setData(padToCandles(volumeDataRef.current, candleDataRef.current));
        syncTimeScales(chartRef.current, volChart);
        return () => cleanupChart(volumeChartRef);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- imperative volume sub-chart create+cleanup; isDark/height retinted by adjacent effects, candle data read via ref
    }, [showVolume, data, isFullscreen]);

    useEffect(() => {
        if (volumeChartRef.current) volumeChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!showInvestorCount || !investorCountContainerRef.current || !data?.candles?.length) {
            cleanupChart(investorCountChartRef);
            return;
        }
        cleanupChart(investorCountChartRef);
        const chart = createSubChart(investorCountContainerRef.current, isDark, histogramHeight);
        investorCountChartRef.current = chart;
        const icData = data.candles
            .filter(c => c.investorCount != null)
            .map(c => ({
                time: new Date(c.candleDate || c.date).getTime() / 1000,
                value: c.investorCount,
                color: '#6366f1',
            }));
        if (icData.length) {
            const series = chart.addSeries(HistogramSeries, { color: '#6366f1', priceFormat: { type: 'volume' } });
            series.setData(icData);
        }
        syncTimeScales(chartRef.current, chart);
        return () => cleanupChart(investorCountChartRef);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- imperative investor-count sub-chart create+cleanup; isDark/height retinted by adjacent effects, candle data read via ref
    }, [showInvestorCount, data, isFullscreen]);

    useEffect(() => {
        if (investorCountChartRef.current) investorCountChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!showPortfolioSize || !portfolioSizeContainerRef.current || !data?.candles?.length) {
            cleanupChart(portfolioSizeChartRef);
            return;
        }
        cleanupChart(portfolioSizeChartRef);
        const chart = createSubChart(portfolioSizeContainerRef.current, isDark, histogramHeight);
        portfolioSizeChartRef.current = chart;
        const psData = data.candles
            .filter(c => c.portfolioSize != null)
            .map(c => ({
                time: new Date(c.candleDate || c.date).getTime() / 1000,
                value: c.portfolioSize,
                color: '#10b981',
            }));
        if (psData.length) {
            const series = chart.addSeries(HistogramSeries, { color: '#10b981', priceFormat: { type: 'volume' } });
            series.setData(psData);
        }
        syncTimeScales(chartRef.current, chart);
        return () => cleanupChart(portfolioSizeChartRef);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- imperative portfolio-size sub-chart create+cleanup; isDark/height retinted by adjacent effects, candle data read via ref
    }, [showPortfolioSize, data, isFullscreen]);

    useEffect(() => {
        if (portfolioSizeChartRef.current) portfolioSizeChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    // Drive the sub-panel values from the MAIN chart's crosshair: on hover look up the hovered time in each
    // cached series; off-chart fall back to the last point. Re-subscribes whenever the main chart is rebuilt.
    useEffect(() => {
        const mainChart = chartRef.current;
        if (!mainChart) return undefined;
        mainChart.subscribeCrosshairMove(applyCrosshair);
        return () => { try { mainChart.unsubscribeCrosshairMove(applyCrosshair); } catch { /* removed */ } };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-subscribe when the main chart is rebuilt (data/fullscreen)
    }, [applyCrosshair, data, isFullscreen]);

    return { rsiContainerRef, macdContainerRef, volumeContainerRef, investorCountContainerRef, portfolioSizeContainerRef, subValues };
};

export default useSubCharts;
