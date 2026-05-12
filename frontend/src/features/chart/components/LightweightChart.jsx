import React, { useMemo, useRef, useEffect, useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
    BarChart2, X, LineChart, Activity, PenTool, Triangle, Calendar,
} from 'lucide-react';
import { useTheme } from '../../../shared/context/ThemeContext';
import useAppStore from '../../../shared/stores/useAppStore';
import useChartConfig from '../hooks/useChartConfig';
import useIndicators from '../hooks/useIndicators';
import useDrawings from '../hooks/useDrawings';
import useFibonacci from '../hooks/useFibonacci';
import useChartCore from '../hooks/useChartCore';
import useSubCharts from '../hooks/useSubCharts';
import useChartDrawing from '../hooks/useChartDrawing';
import IndicatorPanel from './IndicatorPanel';
import DrawingPanel from './DrawingPanel';
import FibonacciPanel from './FibonacciPanel';
import ChartToolbar from './ChartToolbar';
import Card from '../../../shared/components/card';

const TABS = [
    { id: 'indicators', labelKey: 'chart.tabs.indicators', Icon: Activity },
    { id: 'drawings', labelKey: 'chart.tabs.drawings', Icon: PenTool },
    { id: 'fibonacci', labelKey: 'chart.tabs.fibonacci', Icon: Triangle },
];

const TIME_RANGES = [
    { id: '1M', labelKey: 'chart.range.1M', months: 1 },
    { id: '3M', labelKey: 'chart.range.3M', months: 3 },
    { id: '6M', labelKey: 'chart.range.6M', months: 6 },
    { id: '1Y', labelKey: 'chart.range.1Y', months: 12 },
    { id: '5Y', labelKey: 'chart.range.5Y', months: 60 },
    { id: 'ALL', labelKey: 'chart.range.ALL', months: 0 },
];

const LightweightChart = ({ data, symbol, assetType = 'CRYPTO', compareData = null, compareSymbol = null, timeRange = '1Y', onTimeRangeChange }) => {
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
        if (document.fullscreenElement) {
            document.exitFullscreen?.();
        } else {
            wrapperRef.current?.requestFullscreen?.();
        }
    }, []);

    const isFund = assetType === 'FUND';
    const isCrypto = assetType === 'CRYPTO';
    const isForex = assetType === 'FOREX';
    const showVolumeToggle = !isFund && !isForex;
    const showFibTab = !isFund;
    const allowCandle = !isFund && !isForex;

    const sidebarOpen = useAppStore((s) => s.chartSidebarOpen);
    const setSidebarOpen = useAppStore((s) => s.setChartSidebarOpen);
    const activeTab = useAppStore((s) => s.chartActiveTab);
    const setActiveTab = useAppStore((s) => s.setChartActiveTab);

    const { config, setField } = useChartConfig(assetType, symbol, timeRange, !compareSymbol);
    const showVolume = config?.showVolume ?? false;
    const chartType = config?.chartType ?? ((isFund || isForex) ? 'line' : 'candle');
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
        compareData: compareData, compareSymbol, timeRange,
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
            <div className="flex flex-col items-center justify-center h-80 rounded-xl border border-border-default bg-bg-elevated card-elevated">
                <LineChart className="w-12 h-12 mb-3 text-fg-subtle" />
                <p className="text-fg-muted text-sm">{t('chart.waitingForData')}</p>
            </div>
        );
    }

    return (
        <Card ref={wrapperRef} variant="elevated" radius="xl" padding="none" backdropBlur interactive={false} className={`flex ${isFullscreen ? 'h-screen !rounded-none' : ''}`} style={isFullscreen ? {} : { minHeight: 560 }}>
            {sidebarOpen && (
                <div className="w-60 shrink-0 border-r border-border-default flex flex-col bg-surface/40 backdrop-blur-md relative">
                    <div className="pointer-events-none absolute inset-y-0 left-0 w-px bg-gradient-to-b from-indigo-400/40 via-fuchsia-400/20 to-transparent" />
                    <div className="pointer-events-none absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-indigo-400/20 to-transparent" />
                    <div className="flex border-b border-border-default">
                        {TABS.filter(tab => showFibTab || tab.id !== 'fibonacci').map(({ id, labelKey, Icon }) => {
                            const isActive = activeTab === id;
                            return (
                                <button
                                    key={id}
                                    onClick={() => setActiveTab(id)}
                                    className={`relative flex-1 flex flex-col items-center gap-1 py-3 px-1 text-[9px] font-semibold uppercase tracking-[0.04em] border-none cursor-pointer transition-all duration-200 bg-transparent hover:bg-surface/60 min-w-0 ${isActive ? 'text-fg' : 'text-fg-muted hover:text-fg'}`}
                                >
                                    <Icon className={`w-4 h-4 transition-all ${isActive ? 'text-indigo-400 drop-shadow-[0_0_6px_rgba(99,102,241,0.5)]' : ''}`} />
                                    {t(labelKey)}
                                    {isActive && (
                                        <span className="absolute bottom-0 left-2 right-2 h-[2px] rounded-full bg-gradient-to-r from-indigo-400 via-fuchsia-400 to-indigo-400 shadow-[0_0_8px_rgba(99,102,241,0.6)]" />
                                    )}
                                </button>
                            );
                        })}
                    </div>
                    <div className="flex-1 overflow-y-auto p-3 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar-thumb]:bg-border-default [&::-webkit-scrollbar-thumb]:rounded-full hover:[&::-webkit-scrollbar-thumb]:bg-border-hover" style={{ scrollbarWidth: 'thin' }}>
                        {activeTab === 'indicators' && (
                            <IndicatorPanel
                                indicators={indicators}
                                addIndicator={addIndicator}
                                removeIndicator={removeIndicator}
                                updateIndicator={updateIndicator}
                                toggleIndicator={toggleIndicator}
                                allowedTypes={isFund ? ['SMA', 'EMA'] : undefined}
                            />
                        )}
                        {activeTab === 'drawings' && (
                            <DrawingPanel
                                activeTool={activeTool}
                                selectTool={handleSelectTool}
                                cancelTool={cancelTool}
                                drawings={drawings}
                                removeDrawing={removeDrawing}
                                undoDrawing={undoDrawing}
                                clearDrawings={clearDrawings}
                                selectedIcon={selectedIcon}
                                setSelectedIcon={setSelectedIcon}
                                iconSize={iconSize}
                                setIconSize={setIconSize}
                                onHighlight={highlightDrawing}
                            />
                        )}
                        {activeTab === 'fibonacci' && (
                            <FibonacciPanel
                                activeFibTool={activeFibTool}
                                selectFibTool={handleSelectFibTool}
                                cancelFibTool={cancelFibTool}
                                fibTools={fibTools}
                                removeFibTool={removeFibTool}
                                clearFibTools={clearFibTools}
                                onHighlight={highlightFib}
                            />
                        )}
                    </div>
                    {(showVolumeToggle || isFund) && (
                    <div className="border-t border-border-default px-3 pt-2.5 pb-3 space-y-1.5">
                        <p className="text-[9px] font-bold uppercase tracking-[0.16em] text-fg-subtle pb-1">{t('lightweightChart.view')}</p>
                        {showVolumeToggle && (
                            <button
                                onClick={() => setShowVolume(!showVolume)}
                                className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all duration-200 cursor-pointer ${showVolume ? 'border-emerald-400/40 bg-emerald-400/10 text-emerald-400 shadow-[0_0_12px_rgba(52,211,153,0.15)]' : 'border-border-default bg-transparent text-fg-muted hover:text-fg hover:border-border-hover'}`}
                            >
                                <BarChart2 className="w-3.5 h-3.5" />
                                {t('chart.volume')}
                            </button>
                        )}
                        {isFund && (
                            <>
                                <button
                                    onClick={() => hasInvestorCountData && setShowInvestorCount(!showInvestorCount)}
                                    disabled={!hasInvestorCountData}
                                    className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all duration-200 ${!hasInvestorCountData ? 'opacity-45 cursor-not-allowed border-border-default text-fg-subtle' : showInvestorCount ? 'cursor-pointer border-indigo-400/40 bg-indigo-400/10 text-indigo-400 shadow-[0_0_12px_rgba(99,102,241,0.15)]' : 'cursor-pointer border-border-default text-fg-muted hover:text-fg hover:border-border-hover'}`}
                                    title={hasInvestorCountData ? t('lightweightChart.investorCount') : t('lightweightChart.noInvestorCountData')}
                                >
                                    <Activity className="w-3.5 h-3.5" />
                                    {t('lightweightChart.investorCount')}
                                </button>
                                <button
                                    onClick={() => hasPortfolioSizeData && setShowPortfolioSize(!showPortfolioSize)}
                                    disabled={!hasPortfolioSizeData}
                                    className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all duration-200 ${!hasPortfolioSizeData ? 'opacity-45 cursor-not-allowed border-border-default text-fg-subtle' : showPortfolioSize ? 'cursor-pointer border-emerald-500/40 bg-emerald-500/10 text-emerald-500 shadow-[0_0_12px_rgba(16,185,129,0.15)]' : 'cursor-pointer border-border-default text-fg-muted hover:text-fg hover:border-border-hover'}`}
                                    title={hasPortfolioSizeData ? t('lightweightChart.portfolioSize') : t('lightweightChart.noPortfolioSizeData')}
                                >
                                    <BarChart2 className="w-3.5 h-3.5" />
                                    {t('lightweightChart.portfolioSize')}
                                </button>
                            </>
                        )}
                    </div>
                    )}
                </div>
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
                <div className="flex items-center gap-1 px-3 py-1.5 border-b border-border-default bg-surface/40">
                    <Calendar className="w-3 h-3 mr-1 text-fg-subtle" />
                    {TIME_RANGES.map(({ id, labelKey }) => {
                        const isActive = timeRange === id;
                        return (
                            <button
                                key={id}
                                onClick={() => onTimeRangeChange?.(id)}
                                className={`px-2.5 py-1 rounded-md text-[11px] font-semibold tracking-wide border-none cursor-pointer transition-all duration-200 ${isActive ? 'bg-indigo-400/15 text-indigo-400 shadow-[0_0_12px_rgba(99,102,241,0.18)]' : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface'}`}
                            >
                                {t(labelKey)}
                            </button>
                        );
                    })}
                </div>
                <div className={`relative flex-1 ${isFullscreen ? 'min-h-0' : 'min-h-[400px]'}`}>
                    <div ref={chartContainerRef} className="w-full h-full" />
                    <canvas
                        ref={canvasOverlayRef}
                        className="absolute inset-0 w-full h-full"
                        style={{
                            cursor: isAnyToolActive ? 'crosshair' : 'default',
                            zIndex: isAnyToolActive ? 10 : 1,
                            pointerEvents: isAnyToolActive ? 'auto' : 'none',
                        }}
                        onMouseDown={handleMouseDown}
                        onMouseMove={handleMouseMove}
                        onMouseUp={handleMouseUp}
                        onMouseLeave={handleMouseLeave}
                    />
                    <canvas
                        ref={freehandCanvasRef}
                        className="absolute inset-0 w-full h-full pointer-events-none"
                        style={{ zIndex: 11 }}
                    />
                    {textEditState && (
                        <input
                            autoFocus
                            type="text"
                            placeholder={t('chart.textInputPlaceholder')}
                            className="absolute outline-none"
                            style={{
                                left: textEditState.x,
                                top: textEditState.y - 18,
                                fontSize: 14,
                                fontFamily: 'Inter, sans-serif',
                                fontWeight: 500,
                                color: isDark ? '#EDEDEF' : '#0f172a',
                                background: isDark ? 'rgba(10,10,14,0.95)' : '#ffffff',
                                border: '1.5px solid #5E6AD2',
                                borderRadius: 6,
                                padding: '4px 8px',
                                zIndex: 20,
                                minWidth: 80,
                                maxWidth: 320,
                                caretColor: '#5E6AD2',
                                boxShadow: '0 2px 8px rgba(94,106,210,0.18)',
                                letterSpacing: '0.01em',
                            }}
                            onChange={(e) => {
                                const el = e.target;
                                el.style.width = '0';
                                el.style.width = `${Math.max(80, Math.min(320, el.scrollWidth + 16))}px`;
                            }}
                            onKeyDown={(e) => {
                                e.stopPropagation();
                                if (e.key === 'Enter') {
                                    e.preventDefault();
                                    textDoneRef.current = true;
                                    commitTextEdit(e.target.value);
                                } else if (e.key === 'Escape') {
                                    textDoneRef.current = true;
                                    cancelTextEdit();
                                }
                            }}
                            onBlur={(e) => {
                                if (textDoneRef.current) return;
                                textDoneRef.current = true;
                                if (e.target.value.trim()) commitTextEdit(e.target.value);
                                else cancelTextEdit();
                            }}
                        />
                    )}
                </div>
                {hasRSI && (
                    <div className="border-t border-border-default flex-shrink-0">
                        <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <Activity className="w-3.5 h-3.5" style={{ color: rsiIndicator?.color || '#e91e63' }} />
                                RSI {rsiIndicator?.period || 14}
                            </span>
                            <button
                                onClick={() => { const rsi = indicators.find(i => i.type === 'RSI'); if (rsi) toggleIndicator(rsi.id); }}
                                className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                            >
                                <X className="w-3.5 h-3.5" />
                            </button>
                        </div>
                        <div ref={rsiContainerRef} />
                    </div>
                )}
                {hasMACD && (
                    <div className="border-t border-border-default flex-shrink-0">
                        <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <Activity className="w-3.5 h-3.5" style={{ color: macdIndicator?.color || '#06b6d4' }} />
                                MACD (12, 26, 9)
                            </span>
                            <button
                                onClick={() => { const m = indicators.find(i => i.type === 'MACD'); if (m) toggleIndicator(m.id); }}
                                className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                            >
                                <X className="w-3.5 h-3.5" />
                            </button>
                        </div>
                        <div ref={macdContainerRef} />
                    </div>
                )}
                {showVolume && (
                    <div className="border-t border-border-default flex-shrink-0">
                        <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <BarChart2 className="w-3.5 h-3.5 text-emerald-400" />
                                {t('chart.volume')}
                            </span>
                            <button
                                onClick={() => setShowVolume(false)}
                                className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                            >
                                <X className="w-3.5 h-3.5" />
                            </button>
                        </div>
                        <div ref={volumeContainerRef} />
                    </div>
                )}
                {isFund && showInvestorCount && (
                    <div className="border-t border-border-default flex-shrink-0">
                        <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <Activity className="w-3.5 h-3.5 text-indigo-400" />
                                {t('lightweightChart.investorCount')}
                            </span>
                            <button
                                onClick={() => setShowInvestorCount(false)}
                                className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                            >
                                <X className="w-3.5 h-3.5" />
                            </button>
                        </div>
                        <div ref={investorCountContainerRef} />
                    </div>
                )}
                {isFund && showPortfolioSize && (
                    <div className="border-t border-border-default flex-shrink-0">
                        <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <BarChart2 className="w-3.5 h-3.5 text-emerald-500" />
                                {t('lightweightChart.portfolioSize')}
                            </span>
                            <button
                                onClick={() => setShowPortfolioSize(false)}
                                className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                            >
                                <X className="w-3.5 h-3.5" />
                            </button>
                        </div>
                        <div ref={portfolioSizeContainerRef} />
                    </div>
                )}

            </div>
        </Card>
    );
};

export default LightweightChart;
