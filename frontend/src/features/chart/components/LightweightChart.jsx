import React, { useMemo, useRef, useEffect, useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
    LineChart, Activity, PenTool, Triangle, Calendar,
} from 'lucide-react';
import { useTheme } from '../../../shared/context/useTheme';
import useAppStore from '../../../shared/stores/useAppStore';
import useChartConfig from '../hooks/useChartConfig';
import useIndicators from '../hooks/useIndicators';
import useDrawings from '../hooks/useDrawings';
import useFibonacci from '../hooks/useFibonacci';
import useChartCore from '../hooks/useChartCore';
import useSubCharts from '../hooks/useSubCharts';
import useChartDrawing from '../hooks/useChartDrawing';
import ChartToolbar from './ChartToolbar';
import ChartSidebar from './ChartSidebar';
import ChartSubPanels from './ChartSubPanels';
import ChartTextEditInput from './ChartTextEditInput';
import Card from '../../../shared/components/card';

const TABS = [
    { id: 'indicators', labelKey: 'chart.tabs.indicators', Icon: Activity },
    { id: 'drawings', labelKey: 'chart.tabs.drawings', Icon: PenTool },
    { id: 'fibonacci', labelKey: 'chart.tabs.fibonacci', Icon: Triangle },
];

const TIME_RANGES_FULL = [
    { id: '1W', labelKey: 'chart.range.1W', months: 0 },
    { id: '1M', labelKey: 'chart.range.1M', months: 1 },
    { id: '3M', labelKey: 'chart.range.3M', months: 3 },
    { id: '6M', labelKey: 'chart.range.6M', months: 6 },
    { id: '1Y', labelKey: 'chart.range.1Y', months: 12 },
    { id: '3Y', labelKey: 'chart.range.3Y', months: 36 },
    { id: '5Y', labelKey: 'chart.range.5Y', months: 60 },
    { id: 'ALL', labelKey: 'chart.range.ALL', months: 0 },
];

const TIME_RANGES_VIOP = TIME_RANGES_FULL.filter(({ id }) => id !== '5Y' && id !== 'ALL');

const LightweightChart = ({ data, symbol, assetType = 'CRYPTO', compareDatas = [], timeRange = '1Y', onTimeRangeChange, showSecondaryLines = true, onToggleSecondaryLines }) => {
    const compareSymbol = compareDatas.length > 0 ? compareDatas.map(c => c.symbol).join(',') : null;
    const TIME_RANGES = assetType === 'VIOP' ? TIME_RANGES_VIOP : TIME_RANGES_FULL;
    const { t } = useTranslation();
    const { isDark } = useTheme();
    const renderDrawingsRef = useRef(null);
    const textDoneRef = useRef(false);
    const wrapperRef = useRef(null);
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [highlight, setHighlight] = useState(null);

    const triggerHighlight = useCallback((kind, id) => {
        if (!id) return;
        setHighlight({ kind, id, startMs: Date.now() });
    }, []);

    const highlightDrawing = useCallback((id) => triggerHighlight('drawing', id), [triggerHighlight]);
    const highlightFib = useCallback((id) => triggerHighlight('fib', id), [triggerHighlight]);

    useEffect(() => {
        if (!highlight) return undefined;
        const DURATION = 2200;
        let raf;
        const tick = () => {
            const elapsed = Date.now() - highlight.startMs;
            renderDrawingsRef.current?.();
            if (elapsed > DURATION) {
                setHighlight(null);
                return;
            }
            raf = requestAnimationFrame(tick);
        };
        raf = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(raf);
    }, [highlight]);

    useEffect(() => {
        const onChange = () => setIsFullscreen(document.fullscreenElement === wrapperRef.current);
        document.addEventListener('fullscreenchange', onChange);
        return () => document.removeEventListener('fullscreenchange', onChange);
    }, []);

    const toggleFullscreen = useCallback(() => {
        const wrapper = wrapperRef.current;
        if (!wrapper) return;
        const nativeSupported = typeof wrapper.requestFullscreen === 'function'
            && typeof document.exitFullscreen === 'function';
        if (nativeSupported) {
            if (document.fullscreenElement === wrapper) {
                document.exitFullscreen?.();
            } else {
                wrapper.requestFullscreen?.().catch(() => {
                    wrapper.classList.toggle('chart-pseudo-fullscreen');
                    setIsFullscreen((prev) => !prev);
                });
            }
            return;
        }
        wrapper.classList.toggle('chart-pseudo-fullscreen');
        setIsFullscreen((prev) => !prev);
    }, []);

    const isFund = assetType === 'FUND';
    const isForex = assetType === 'FOREX';
    const isViop = assetType === 'VIOP';
    const showVolumeToggle = !isFund && !isForex && !isViop;
    const showFibTab = !isFund;
    const allowCandle = !isFund && !isForex && !isViop;

    const sidebarOpen = useAppStore((s) => s.chartSidebarOpen);
    const setSidebarOpen = useAppStore((s) => s.setChartSidebarOpen);
    const activeTab = useAppStore((s) => s.chartActiveTab);
    const setActiveTab = useAppStore((s) => s.setChartActiveTab);

    const { config, setField } = useChartConfig(assetType, symbol, timeRange, !compareSymbol);
    const showVolume = config?.showVolume ?? false;
    const chartType = config?.chartType ?? ((isFund || isForex || isViop) ? 'line' : 'candle');
    const magnetMode = (['off', 'weak', 'strong'].includes(config?.magnetMode)) ? config.magnetMode : 'off';
    const selectedIcon = config?.selectedIcon ?? '\u{1F680}';
    const iconSize = config?.iconSize ?? 22;
    const showInvestorCount = config?.showInvestorCount ?? false;
    const showPortfolioSize = config?.showPortfolioSize ?? false;

    const setShowVolume = useCallback((v) => setField('showVolume', typeof v === 'function' ? v(showVolume) : v), [setField, showVolume]);
    const setChartType = useCallback((v) => setField('chartType', v), [setField]);
    const setMagnetMode = useCallback((v) => setField('magnetMode', v), [setField]);
    const setSelectedIcon = useCallback((v) => setField('selectedIcon', v), [setField]);
    const setIconSize = useCallback((v) => setField('iconSize', typeof v === 'function' ? v(iconSize) : v), [setField, iconSize]);
    const setShowInvestorCount = useCallback((v) => setField('showInvestorCount', typeof v === 'function' ? v(showInvestorCount) : v), [setField, showInvestorCount]);
    const setShowPortfolioSize = useCallback((v) => setField('showPortfolioSize', typeof v === 'function' ? v(showPortfolioSize) : v), [setField, showPortfolioSize]);

    const hasInvestorCountData = useMemo(() =>
        isFund && data?.candles?.some(c => c.investorCount != null && Number(c.investorCount) > 0), [data, isFund]);
    const hasPortfolioSizeData = useMemo(() =>
        isFund && data?.candles?.some(c => c.portfolioSize != null && Number(c.portfolioSize) > 0), [data, isFund]);

    const { indicators, addIndicator, removeIndicator, updateIndicator, toggleIndicator } = useIndicators(assetType, symbol, timeRange, !compareSymbol);
    const { drawings, activeTool, addDrawing, removeDrawing, undoDrawing, clearDrawings, selectTool, cancelTool } = useDrawings(assetType, symbol, timeRange, !compareSymbol);
    const { fibTools, activeFibTool, addFibTool, removeFibTool, clearFibTools, selectFibTool, cancelFibTool } = useFibonacci(assetType, symbol, timeRange, !compareSymbol);

    const filteredIndicators = useMemo(() => {
        if (isFund) return indicators.filter(i => i.type === 'SMA' || i.type === 'EMA');
        return indicators;
    }, [indicators, isFund]);

    const hasRSI = useMemo(() => !isFund && indicators.some(i => i.type === 'RSI' && i.visible), [indicators, isFund]);
    const rsiIndicator = useMemo(() => indicators.find(i => i.type === 'RSI' && i.visible), [indicators]);
    const hasMACD = useMemo(() => !isFund && indicators.some(i => i.type === 'MACD' && i.visible), [indicators, isFund]);
    const macdIndicator = useMemo(() => indicators.find(i => i.type === 'MACD' && i.visible), [indicators]);

    const { chartRef, chartContainerRef, candleSeriesRef, candleDataRef, volumeDataRef, trend, crosshairData } = useChartCore({
        data: data, symbol, chartType: allowCandle ? chartType : 'line', isDark, indicators: filteredIndicators, renderDrawingsRef, assetType,
        compareDatas, timeRange, showSecondaryLines,
    });

    const { rsiContainerRef, macdContainerRef, volumeContainerRef, investorCountContainerRef, portfolioSizeContainerRef } = useSubCharts({
        chartRef, candleDataRef, volumeDataRef, isDark,
        hasRSI, rsiIndicator, hasMACD, macdIndicator, showVolume: showVolumeToggle && showVolume, data: data,
        showInvestorCount: isFund && showInvestorCount,
        showPortfolioSize: isFund && showPortfolioSize,
        isFullscreen,
    });

    const {
        canvasOverlayRef, freehandCanvasRef,
        handleMouseDown, handleMouseMove, handleMouseUp, handleMouseLeave,
        isAnyToolActive, handleSelectTool, handleSelectFibTool, cancelAllDrawing,
        textEditState, commitTextEdit, cancelTextEdit,
    } = useChartDrawing({
        chartRef, candleSeriesRef, candleDataRef, isDark,
        drawings, addDrawing, cancelTool,
        fibTools: showFibTab ? fibTools : [], addFibTool, cancelFibTool,
        activeTool, activeFibTool: showFibTab ? activeFibTool : null,
        magnetMode, selectedIcon, iconSize,
        data: data, symbol, renderDrawingsRef,
        selectTool, selectFibTool,
        highlight,
    });

    useEffect(() => {
        if (textEditState) textDoneRef.current = false;
    }, [textEditState]);

    if (!data?.candles?.length) {
        return (
            <Card variant="elevated" radius="xl" padding="lg" backdropBlur interactive={false} className="flex flex-col items-center justify-center h-80">
                <LineChart className="w-12 h-12 mb-3 text-fg-subtle" />
                <p className="text-fg-muted text-sm">{t('chart.waitingForData')}</p>
            </Card>
        );
    }

    return (
        <Card ref={wrapperRef} variant="elevated" radius="xl" padding="none" backdropBlur interactive={false} className={`flex flex-col lg:flex-row !overflow-x-hidden !overflow-y-visible ${isFullscreen ? 'h-screen !rounded-none !overflow-y-auto' : 'min-h-[320px] sm:min-h-[440px] lg:min-h-[560px]'}`}>
            <button
                type="button"
                data-tour="chart-drawing-open"
                aria-hidden="true"
                tabIndex={-1}
                onClick={() => { setSidebarOpen(true); setActiveTab('drawings'); }}
                style={{ position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap', border: 0 }}
            />
            <button
                type="button"
                data-tour-close="chart-drawing"
                aria-hidden="true"
                tabIndex={-1}
                onClick={() => { setSidebarOpen(false); }}
                style={{ position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap', border: 0 }}
            />
            {sidebarOpen && (
                <ChartSidebar
                    tabs={TABS}
                    showFibTab={showFibTab}
                    activeTab={activeTab}
                    setActiveTab={setActiveTab}
                    isFund={isFund}
                    isForex={isForex}
                    showVolumeToggle={showVolumeToggle}
                    indicators={indicators}
                    addIndicator={addIndicator}
                    removeIndicator={removeIndicator}
                    updateIndicator={updateIndicator}
                    toggleIndicator={toggleIndicator}
                    activeTool={activeTool}
                    handleSelectTool={handleSelectTool}
                    cancelTool={cancelTool}
                    drawings={drawings}
                    removeDrawing={removeDrawing}
                    undoDrawing={undoDrawing}
                    clearDrawings={clearDrawings}
                    selectedIcon={selectedIcon}
                    setSelectedIcon={setSelectedIcon}
                    iconSize={iconSize}
                    setIconSize={setIconSize}
                    highlightDrawing={highlightDrawing}
                    activeFibTool={activeFibTool}
                    handleSelectFibTool={handleSelectFibTool}
                    cancelFibTool={cancelFibTool}
                    fibTools={fibTools}
                    removeFibTool={removeFibTool}
                    clearFibTools={clearFibTools}
                    highlightFib={highlightFib}
                    showSecondaryLines={showSecondaryLines}
                    onToggleSecondaryLines={onToggleSecondaryLines}
                    showVolume={showVolume}
                    setShowVolume={setShowVolume}
                    hasInvestorCountData={hasInvestorCountData}
                    showInvestorCount={showInvestorCount}
                    setShowInvestorCount={setShowInvestorCount}
                    hasPortfolioSizeData={hasPortfolioSizeData}
                    showPortfolioSize={showPortfolioSize}
                    setShowPortfolioSize={setShowPortfolioSize}
                />
            )}
            <div className="flex-1 flex flex-col min-w-0">
                <ChartToolbar
                    isDark={isDark}
                    sidebarOpen={sidebarOpen}
                    setSidebarOpen={setSidebarOpen}
                    chartType={allowCandle ? chartType : 'line'}
                    setChartType={allowCandle ? setChartType : () => {}}
                    magnetMode={magnetMode}
                    setMagnetMode={setMagnetMode}
                    symbol={symbol}
                    trend={trend}
                    crosshairData={crosshairData}
                    assetType={assetType}
                    indicators={filteredIndicators}
                    drawings={drawings}
                    isAnyToolActive={isAnyToolActive}
                    activeTool={activeTool}
                    activeFibTool={showFibTab ? activeFibTool : null}
                    cancelAllDrawing={cancelAllDrawing}
                    allowCandle={allowCandle}
                    compareSymbol={compareSymbol}
                    isFullscreen={isFullscreen}
                    onToggleFullscreen={toggleFullscreen}
                />
                <div className="flex items-center gap-1 px-2 sm:px-3 py-1.5 border-b border-border-default bg-surface/40 overflow-x-auto scrollbar-thin">
                    <Calendar className="w-3 h-3 mr-1 text-fg-subtle shrink-0" />
                    {TIME_RANGES.map(({ id, labelKey }) => {
                        const isActive = timeRange === id;
                        return (
                            <button
                                key={id}
                                onClick={() => onTimeRangeChange?.(id)}
                                className={`shrink-0 min-h-[32px] px-2.5 py-1 rounded-md text-[11px] font-semibold tracking-wide border-none cursor-pointer transition-all duration-200 ${isActive ? 'bg-indigo-400/15 text-indigo-400 shadow-[0_0_12px_rgba(99,102,241,0.18)]' : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface'}`}
                            >
                                {t(labelKey)}
                            </button>
                        );
                    })}
                </div>
                <div className={`relative flex-1 ${isFullscreen ? 'min-h-0' : 'min-h-[55vh] sm:min-h-[400px] lg:min-h-[420px]'}`}>
                    <div ref={chartContainerRef} className="w-full h-full" />
                    <canvas
                        ref={canvasOverlayRef}
                        className="absolute inset-0 w-full h-full"
                        style={{
                            cursor: isAnyToolActive ? 'crosshair' : 'default',
                            zIndex: isAnyToolActive ? 10 : 1,
                            pointerEvents: isAnyToolActive ? 'auto' : 'none',
                            touchAction: isAnyToolActive ? 'none' : 'auto',
                        }}
                        onMouseDown={handleMouseDown}
                        onMouseMove={handleMouseMove}
                        onMouseUp={handleMouseUp}
                        onMouseLeave={handleMouseLeave}
                        onTouchStart={handleMouseDown}
                        onTouchMove={handleMouseMove}
                        onTouchEnd={handleMouseUp}
                        onTouchCancel={handleMouseLeave}
                    />
                    <canvas
                        ref={freehandCanvasRef}
                        className="absolute inset-0 w-full h-full pointer-events-none"
                        style={{ zIndex: 11 }}
                    />
                    <ChartTextEditInput
                        textEditState={textEditState}
                        isDark={isDark}
                        textDoneRef={textDoneRef}
                        commitTextEdit={commitTextEdit}
                        cancelTextEdit={cancelTextEdit}
                    />
                </div>
                <ChartSubPanels
                    hasRSI={hasRSI}
                    rsiIndicator={rsiIndicator}
                    rsiContainerRef={rsiContainerRef}
                    hasMACD={hasMACD}
                    macdIndicator={macdIndicator}
                    macdContainerRef={macdContainerRef}
                    indicators={indicators}
                    toggleIndicator={toggleIndicator}
                    showVolume={showVolume}
                    setShowVolume={setShowVolume}
                    volumeContainerRef={volumeContainerRef}
                    isFund={isFund}
                    showInvestorCount={showInvestorCount}
                    setShowInvestorCount={setShowInvestorCount}
                    investorCountContainerRef={investorCountContainerRef}
                    showPortfolioSize={showPortfolioSize}
                    setShowPortfolioSize={setShowPortfolioSize}
                    portfolioSizeContainerRef={portfolioSizeContainerRef}
                />

            </div>
        </Card>
    );
};

export default LightweightChart;
