import { useEffect, useRef } from 'react';
import { createChart, LineSeries, HistogramSeries } from 'lightweight-charts';
import { calculateRSI, calculateMACD } from './indicators';
import { getChartOptions } from './chartOptions';

const createSubChart = (container, isDark, height) => {
    const opts = getChartOptions(isDark);
    return createChart(container, {
        ...opts,
        width: container.clientWidth,
        height,
        timeScale: { ...opts.timeScale, visible: false },
    });
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
        chartRef.current.remove();
        chartRef.current = null;
    }
};

const useSubCharts = ({ chartRef, candleDataRef, volumeDataRef, isDark, hasRSI, rsiIndicator, hasMACD, macdIndicator, showVolume, data, showInvestorCount, showPortfolioSize }) => {
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
        const rsiChart = createSubChart(rsiContainerRef.current, isDark, 150);
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
    }, [hasRSI, rsiIndicator, data]);

    useEffect(() => {
        if (rsiChartRef.current) rsiChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!hasMACD || !macdContainerRef.current || !candleDataRef.current.length) {
            cleanupChart(macdChartRef);
            return;
        }
        cleanupChart(macdChartRef);
        const macdChart = createSubChart(macdContainerRef.current, isDark, 150);
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
    }, [hasMACD, macdIndicator, data]);

    useEffect(() => {
        if (macdChartRef.current) macdChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!showVolume || !volumeContainerRef.current || !volumeDataRef.current.length) {
            cleanupChart(volumeChartRef);
            return;
        }
        const volChart = createSubChart(volumeContainerRef.current, isDark, 120);
        volumeChartRef.current = volChart;
        const volSeries = volChart.addSeries(HistogramSeries, { color: '#26a69a', priceFormat: { type: 'volume' } });
        volSeries.setData(volumeDataRef.current);
        syncTimeScales(chartRef.current, volChart);
        return () => cleanupChart(volumeChartRef);
    }, [showVolume, data]);

    useEffect(() => {
        if (volumeChartRef.current) volumeChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!showInvestorCount || !investorCountContainerRef.current || !data?.candles?.length) {
            cleanupChart(investorCountChartRef);
            return;
        }
        cleanupChart(investorCountChartRef);
        const chart = createSubChart(investorCountContainerRef.current, isDark, 120);
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
    }, [showInvestorCount, data]);

    useEffect(() => {
        if (investorCountChartRef.current) investorCountChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!showPortfolioSize || !portfolioSizeContainerRef.current || !data?.candles?.length) {
            cleanupChart(portfolioSizeChartRef);
            return;
        }
        cleanupChart(portfolioSizeChartRef);
        const chart = createSubChart(portfolioSizeContainerRef.current, isDark, 120);
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
    }, [showPortfolioSize, data]);

    useEffect(() => {
        if (portfolioSizeChartRef.current) portfolioSizeChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    return { rsiContainerRef, macdContainerRef, volumeContainerRef, investorCountContainerRef, portfolioSizeContainerRef };
};

export default useSubCharts;
