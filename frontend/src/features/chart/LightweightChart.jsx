import React, { useState, useMemo, useRef, useEffect } from 'react';
import {
    BarChart2, X, LineChart, Activity, PenTool, Triangle,
} from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
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

const TABS = [
    { id: 'indicators', label: 'Indicators', Icon: Activity },
    { id: 'drawings', label: 'Draw', Icon: PenTool },
    { id: 'fibonacci', label: 'Fibonacci', Icon: Triangle },
];

const LightweightChart = ({ data, symbol, assetType = 'CRYPTO', compareData = null, compareSymbol = null }) => {
    const { isDark } = useTheme();
    const renderDrawingsRef = useRef(null);
    const textDoneRef = useRef(false);
    const [activeTab, setActiveTab] = useState('indicators');
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [showVolume, setShowVolume] = useState(assetType === 'CRYPTO');
    const [chartType, setChartType] = useState(assetType === 'FUND' ? 'line' : 'candle');
    const [magnetMode, setMagnetMode] = useState('off');
    const [selectedIcon, setSelectedIcon] = useState('\u{1F680}');
    const [iconSize, setIconSize] = useState(22);

    const isFund = assetType === 'FUND';
    const isCrypto = assetType === 'CRYPTO';
    const isForex = assetType === 'FOREX';
    const showVolumeToggle = !isFund && !isForex;
    const showFibTab = !isFund;
    const allowCandle = !isFund;
    const [showInvestorCount, setShowInvestorCount] = useState(false);
    const [showPortfolioSize, setShowPortfolioSize] = useState(false);

    const hasInvestorCountData = useMemo(() =>
        isFund && data?.candles?.some(c => c.investorCount != null), [data, isFund]);
    const hasPortfolioSizeData = useMemo(() =>
        isFund && data?.candles?.some(c => c.portfolioSize != null), [data, isFund]);

    const { indicators, addIndicator, removeIndicator, updateIndicator, toggleIndicator } = useIndicators();
    const { drawings, activeTool, addDrawing, removeDrawing, undoDrawing, clearDrawings, selectTool, cancelTool } = useDrawings();
    const { fibTools, activeFibTool, addFibTool, removeFibTool, clearFibTools, selectFibTool, cancelFibTool } = useFibonacci();

    const filteredIndicators = useMemo(() => {
        if (isFund) return indicators.filter(i => i.type === 'SMA' || i.type === 'EMA');
        return indicators;
    }, [indicators, isFund]);

    const hasRSI = useMemo(() => !isFund && indicators.some(i => i.type === 'RSI' && i.visible), [indicators, isFund]);
    const rsiIndicator = useMemo(() => indicators.find(i => i.type === 'RSI' && i.visible), [indicators]);
    const hasMACD = useMemo(() => !isFund && indicators.some(i => i.type === 'MACD' && i.visible), [indicators, isFund]);
    const macdIndicator = useMemo(() => indicators.find(i => i.type === 'MACD' && i.visible), [indicators]);

    const { chartRef, chartContainerRef, candleSeriesRef, candleDataRef, volumeDataRef, trend, crosshairData } = useChartCore({
        data, symbol, chartType: allowCandle ? chartType : 'line', isDark, indicators: filteredIndicators, renderDrawingsRef, assetType,
        compareData, compareSymbol,
    });

    const { rsiContainerRef, macdContainerRef, volumeContainerRef, investorCountContainerRef, portfolioSizeContainerRef } = useSubCharts({
        chartRef, candleDataRef, volumeDataRef, isDark,
        hasRSI, rsiIndicator, hasMACD, macdIndicator, showVolume: showVolumeToggle && showVolume, data,
        showInvestorCount: isFund && showInvestorCount,
        showPortfolioSize: isFund && showPortfolioSize,
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
        data, symbol, renderDrawingsRef,
        selectTool, selectFibTool,
    });

    useEffect(() => {
        if (textEditState) textDoneRef.current = false;
    }, [textEditState]);

    if (!data?.candles?.length) {
        return (
            <div className="flex flex-col items-center justify-center h-80 rounded-xl border" style={{ background: isDark ? '#050506' : '#f8fafc', borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                <LineChart className="w-12 h-12 mb-3" style={{ color: isDark ? 'rgba(255,255,255,0.2)' : '#94a3b8' }} />
                <p className="text-fg-muted text-sm">Waiting for chart data...</p>
            </div>
        );
    }

    return (
        <div className="flex rounded-xl border overflow-hidden" style={{ minHeight: 560, background: isDark ? '#020203' : '#f8fafc', borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
            {sidebarOpen && (
                <div className="w-56 shrink-0 border-r flex flex-col" style={{ background: isDark ? '#0a0a0c' : '#eef1f6', borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                    <div className="flex border-b" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                        {TABS.filter(t => showFibTab || t.id !== 'fibonacci').map(({ id, label, Icon }) => (
                            <button
                                key={id}
                                onClick={() => setActiveTab(id)}
                                className="flex-1 flex flex-col items-center gap-0.5 py-2.5 text-[10px] font-semibold uppercase tracking-wider border-none cursor-pointer transition-all duration-150"
                                style={{
                                    background: activeTab === id ? (isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.02)') : 'transparent',
                                    color: activeTab === id ? 'var(--color-fg)' : 'var(--color-fg-muted)',
                                    borderBottom: activeTab === id ? '2px solid #5E6AD2' : '2px solid transparent',
                                }}
                            >
                                <Icon className="w-3.5 h-3.5" style={{ color: activeTab === id ? '#5E6AD2' : undefined }} />
                                {label}
                            </button>
                        ))}
                    </div>
                    <div className="flex-1 overflow-y-auto p-2.5" style={{ scrollbarWidth: 'thin' }}>
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
                            />
                        )}
                    </div>
                    <div className="border-t p-2.5 space-y-1.5" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                        {showVolumeToggle && (
                            <button
                                onClick={() => setShowVolume(!showVolume)}
                                className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all duration-150 cursor-pointer"
                                style={{
                                    background: showVolume ? 'rgba(38,166,154,0.1)' : 'transparent',
                                    borderColor: showVolume ? 'rgba(38,166,154,0.3)' : (isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0'),
                                    color: showVolume ? '#26a69a' : 'var(--color-fg-muted)',
                                }}
                            >
                                <BarChart2 className="w-3.5 h-3.5" />
                                Volume
                            </button>
                        )}
                        {isFund && (
                            <>
                                <button
                                    onClick={() => hasInvestorCountData && setShowInvestorCount(!showInvestorCount)}
                                    disabled={!hasInvestorCountData}
                                    className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all duration-150"
                                    style={{
                                        background: showInvestorCount ? 'rgba(99,102,241,0.1)' : 'transparent',
                                        borderColor: showInvestorCount ? 'rgba(99,102,241,0.3)' : (isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0'),
                                        color: !hasInvestorCountData ? 'var(--color-fg-subtle)' : showInvestorCount ? '#6366f1' : 'var(--color-fg-muted)',
                                        opacity: hasInvestorCountData ? 1 : 0.45,
                                        cursor: hasInvestorCountData ? 'pointer' : 'not-allowed',
                                    }}
                                    title={hasInvestorCountData ? 'Kişi Sayısı' : 'Bu fon için kişi sayısı verisi yok'}
                                >
                                    <Activity className="w-3.5 h-3.5" />
                                    Kişi Sayısı
                                </button>
                                <button
                                    onClick={() => hasPortfolioSizeData && setShowPortfolioSize(!showPortfolioSize)}
                                    disabled={!hasPortfolioSizeData}
                                    className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all duration-150"
                                    style={{
                                        background: showPortfolioSize ? 'rgba(16,185,129,0.1)' : 'transparent',
                                        borderColor: showPortfolioSize ? 'rgba(16,185,129,0.3)' : (isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0'),
                                        color: !hasPortfolioSizeData ? 'var(--color-fg-subtle)' : showPortfolioSize ? '#10b981' : 'var(--color-fg-muted)',
                                        opacity: hasPortfolioSizeData ? 1 : 0.45,
                                        cursor: hasPortfolioSizeData ? 'pointer' : 'not-allowed',
                                    }}
                                    title={hasPortfolioSizeData ? 'Portföy Büyüklüğü' : 'Bu fon için portföy verisi yok'}
                                >
                                    <BarChart2 className="w-3.5 h-3.5" />
                                    Portföy Büyüklüğü
                                </button>
                            </>
                        )}
                    </div>
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
                    indicators={filteredIndicators}
                    drawings={drawings}
                    isAnyToolActive={isAnyToolActive}
                    activeTool={activeTool}
                    activeFibTool={showFibTab ? activeFibTool : null}
                    cancelAllDrawing={cancelAllDrawing}
                    allowCandle={allowCandle}
                    compareSymbol={compareSymbol}
                />
                <div className="relative flex-1">
                    <div ref={chartContainerRef} className="w-full" style={{ height: 500 }} />
                    <canvas
                        ref={canvasOverlayRef}
                        className="absolute inset-0 w-full"
                        style={{
                            height: 500,
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
                        className="absolute inset-0 w-full pointer-events-none"
                        style={{ height: 500, zIndex: 11 }}
                    />
                    {textEditState && (
                        <input
                            autoFocus
                            type="text"
                            placeholder="Type here..."
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
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#eef1f6' }}>
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
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#eef1f6' }}>
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
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#eef1f6' }}>
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <BarChart2 className="w-3.5 h-3.5 text-[#26a69a]" />
                                Volume
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
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#eef1f6' }}>
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <Activity className="w-3.5 h-3.5" style={{ color: '#6366f1' }} />
                                Kişi Sayısı
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
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#eef1f6' }}>
                            <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                                <BarChart2 className="w-3.5 h-3.5" style={{ color: '#10b981' }} />
                                Portföy Büyüklüğü
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
        </div>
    );
};

export default LightweightChart;
