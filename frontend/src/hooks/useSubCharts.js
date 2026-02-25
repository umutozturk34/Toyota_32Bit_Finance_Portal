import { useEffect, useRef } from 'react';
import { createChart, LineSeries, HistogramSeries } from 'lightweight-charts';
import { calculateRSI, calculateMACD } from '../utils/indicators';
import { getChartOptions } from '../utils/chartOptions';

const useSubCharts = ({ chartRef, candleDataRef, volumeDataRef, isDark, hasRSI, rsiIndicator, hasMACD, macdIndicator, showVolume, data }) => {
    const rsiChartRef = useRef(null);
    const rsiContainerRef = useRef(null);
    const rsiSeriesRef = useRef(null);
    const macdChartRef = useRef(null);
    const macdContainerRef = useRef(null);
    const volumeChartRef = useRef(null);
    const volumeContainerRef = useRef(null);

    useEffect(() => {
        if (!hasRSI || !rsiContainerRef.current || !candleDataRef.current.length) {
            if (rsiChartRef.current) { rsiChartRef.current.remove(); rsiChartRef.current = null; }
            return;
        }
        if (rsiChartRef.current) { rsiChartRef.current.remove(); rsiChartRef.current = null; }
        const opts = getChartOptions(isDark);
        const rsiChart = createChart(rsiContainerRef.current, {
            ...opts,
            width: rsiContainerRef.current.clientWidth,
            height: 150,
            timeScale: { ...opts.timeScale, visible: false },
        });
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
        const mainChart = chartRef.current;
        if (mainChart) {
            const syncRange = () => {
                const r = mainChart.timeScale().getVisibleLogicalRange();
                if (r) rsiChart.timeScale().setVisibleLogicalRange(r);
            };
            mainChart.timeScale().subscribeVisibleLogicalRangeChange(syncRange);
            syncRange();
            rsiChart.timeScale().subscribeVisibleLogicalRangeChange(range => {
                if (range) mainChart.timeScale().setVisibleLogicalRange(range);
            });
        }
        return () => {
            if (rsiChartRef.current) { rsiChartRef.current.remove(); rsiChartRef.current = null; }
        };
    }, [hasRSI, rsiIndicator, data]);

    useEffect(() => {
        if (rsiChartRef.current) rsiChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!hasMACD || !macdContainerRef.current || !candleDataRef.current.length) {
            if (macdChartRef.current) { macdChartRef.current.remove(); macdChartRef.current = null; }
            return;
        }
        if (macdChartRef.current) { macdChartRef.current.remove(); macdChartRef.current = null; }
        const opts = getChartOptions(isDark);
        const macdChart = createChart(macdContainerRef.current, {
            ...opts,
            width: macdContainerRef.current.clientWidth,
            height: 150,
            timeScale: { ...opts.timeScale, visible: false },
        });
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
        const mainChart = chartRef.current;
        if (mainChart) {
            const syncRange = () => {
                const r = mainChart.timeScale().getVisibleLogicalRange();
                if (r) macdChart.timeScale().setVisibleLogicalRange(r);
            };
            mainChart.timeScale().subscribeVisibleLogicalRangeChange(syncRange);
            syncRange();
            macdChart.timeScale().subscribeVisibleLogicalRangeChange(range => {
                if (range) mainChart.timeScale().setVisibleLogicalRange(range);
            });
        }
        return () => {
            if (macdChartRef.current) { macdChartRef.current.remove(); macdChartRef.current = null; }
        };
    }, [hasMACD, macdIndicator, data]);

    useEffect(() => {
        if (macdChartRef.current) macdChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    useEffect(() => {
        if (!showVolume || !volumeContainerRef.current || !volumeDataRef.current.length) {
            if (volumeChartRef.current) { volumeChartRef.current.remove(); volumeChartRef.current = null; }
            return;
        }
        const vopts = getChartOptions(isDark);
        const volChart = createChart(volumeContainerRef.current, {
            ...vopts,
            width: volumeContainerRef.current.clientWidth,
            height: 120,
            timeScale: { ...vopts.timeScale, visible: false },
        });
        volumeChartRef.current = volChart;
        const volSeries = volChart.addSeries(HistogramSeries, { color: '#26a69a', priceFormat: { type: 'volume' } });
        volSeries.setData(volumeDataRef.current);
        const mainChart = chartRef.current;
        if (mainChart) {
            const sync = () => {
                const r = mainChart.timeScale().getVisibleLogicalRange();
                if (r) volChart.timeScale().setVisibleLogicalRange(r);
            };
            mainChart.timeScale().subscribeVisibleLogicalRangeChange(sync);
            sync();
            volChart.timeScale().subscribeVisibleLogicalRangeChange(range => {
                if (range) mainChart.timeScale().setVisibleLogicalRange(range);
            });
        }
        return () => { if (volumeChartRef.current) { volumeChartRef.current.remove(); volumeChartRef.current = null; } };
    }, [showVolume, data]);

    useEffect(() => {
        if (volumeChartRef.current) volumeChartRef.current.applyOptions(getChartOptions(isDark));
    }, [isDark]);

    return { rsiContainerRef, macdContainerRef, volumeContainerRef };
};

export default useSubCharts;
