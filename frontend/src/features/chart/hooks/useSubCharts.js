import { useEffect, useRef } from 'react';
import { createChart, LineSeries, HistogramSeries } from 'lightweight-charts';
import { calculateRSI, calculateMACD } from '../lib/indicators';
import { getChartOptions } from '../lib/chartOptions';

const createSubChart = (container, isDark, height) => {
    const opts = getChartOptions(isDark);
    const chart = createChart(container, {
        ...opts,
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
};

const cleanupChart = (chartRef) => {
    if (chartRef.current) {
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
        rsiSeries.setData(rsiData);
        rsiChart.priceScale('right').applyOptions({
            scaleMargins: { top: 0.05, bottom: 0.05 },
            autoScale: false,
        });
        syncTimeScales(chartRef.current, rsiChart);
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
        histSeries.setData(histogram);
        const macdColor = macdIndicator?.color || '#06b6d4';
        const macdSeries = macdChart.addSeries(LineSeries, {
            color: macdColor,
            lineWidth: 2,
            title: 'MACD',
            priceScaleId: 'right',
            crosshairMarkerVisible: false,
        });
        macdSeries.setData(macd);
        const signalSeries = macdChart.addSeries(LineSeries, {
            color: '#f59e0b',
            lineWidth: 1,
            title: 'Signal',
            priceScaleId: 'right',
            crosshairMarkerVisible: false,
        });
        signalSeries.setData(signal);
        macdChart.priceScale('right').applyOptions({
            scaleMargins: { top: 0.1, bottom: 0.1 },
        });
        syncTimeScales(chartRef.current, macdChart);
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
        volSeries.setData(volumeDataRef.current);
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

    return { rsiContainerRef, macdContainerRef, volumeContainerRef, investorCountContainerRef, portfolioSizeContainerRef };
};

export default useSubCharts;
