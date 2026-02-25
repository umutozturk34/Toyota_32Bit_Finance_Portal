import React, { useState, useMemo, useRef } from 'react';
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

const LightweightChart = ({ data, symbol }) => {
    const { isDark } = useTheme();
    const renderDrawingsRef = useRef(null);
    const [activeTab, setActiveTab] = useState('indicators');
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [showVolume, setShowVolume] = useState(false);
    const [chartType, setChartType] = useState('line');
    const [magnetMode, setMagnetMode] = useState('off');
    const [textInput, setTextInput] = useState('Breakout');
    const [textSize, setTextSize] = useState(13);
    const [selectedIcon, setSelectedIcon] = useState('🚀');

    const { indicators, addIndicator, removeIndicator, updateIndicator, toggleIndicator } = useIndicators();
    const { drawings, activeTool, addDrawing, removeDrawing, undoDrawing, clearDrawings, selectTool, cancelTool } = useDrawings();
    const { fibTools, activeFibTool, addFibTool, removeFibTool, clearFibTools, selectFibTool, cancelFibTool } = useFibonacci();

    const hasRSI = useMemo(() => indicators.some(i => i.type === 'RSI' && i.visible), [indicators]);
    const rsiIndicator = useMemo(() => indicators.find(i => i.type === 'RSI' && i.visible), [indicators]);
    const hasMACD = useMemo(() => indicators.some(i => i.type === 'MACD' && i.visible), [indicators]);
    const macdIndicator = useMemo(() => indicators.find(i => i.type === 'MACD' && i.visible), [indicators]);

    const { chartRef, chartContainerRef, candleSeriesRef, candleDataRef, volumeDataRef, trend, crosshairData } = useChartCore({
        data, symbol, chartType, isDark, indicators, renderDrawingsRef,
    });

    const { rsiContainerRef, macdContainerRef, volumeContainerRef } = useSubCharts({
        chartRef, candleDataRef, volumeDataRef, isDark,
        hasRSI, rsiIndicator, hasMACD, macdIndicator, showVolume, data,
    });

    const {
        canvasOverlayRef, freehandCanvasRef,
        handleMouseDown, handleMouseMove, handleMouseUp, handleMouseLeave,
        isAnyToolActive, handleSelectTool, handleSelectFibTool, cancelAllDrawing,
    } = useChartDrawing({
        chartRef, candleSeriesRef, candleDataRef, isDark,
        drawings, addDrawing, cancelTool,
        fibTools, addFibTool, cancelFibTool,
        activeTool, activeFibTool,
        magnetMode, textInput, textSize, selectedIcon,
        data, symbol, renderDrawingsRef,
        selectTool, selectFibTool,
    });

    if (!data?.candles?.length) {
        return (
            <div className="flex flex-col items-center justify-center h-80 rounded-xl border" style={{ background: isDark ? '#050506' : '#f9fafb', borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)' }}>
                <LineChart className="w-12 h-12 mb-3" style={{ color: isDark ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.15)' }} />
                <p className="text-fg-muted text-sm">Waiting for chart data...</p>
            </div>
        );
    }

    return (
        <div className="flex rounded-xl border overflow-hidden" style={{ minHeight: 560, background: isDark ? '#020203' : '#f9fafb', borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)' }}>
            {sidebarOpen && (
                <div className="w-56 shrink-0 border-r flex flex-col" style={{ background: isDark ? '#0a0a0c' : '#f3f4f6', borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
                    <div className="flex border-b" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
                        {TABS.map(({ id, label, Icon }) => (
                            <button
                                key={id}
                                onClick={() => setActiveTab(id)}
                                className="flex-1 flex flex-col items-center gap-0.5 py-2.5 text-[10px] font-semibold uppercase tracking-wider border-none cursor-pointer transition-all duration-150"
                                style={{
                                    background: activeTab === id ? (isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)') : 'transparent',
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
                                textInput={textInput}
                                setTextInput={setTextInput}
                                textSize={textSize}
                                setTextSize={setTextSize}
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
                    <div className="border-t p-2.5 space-y-1.5" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
                        <button
                            onClick={() => setShowVolume(!showVolume)}
                            className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all duration-150 cursor-pointer"
                            style={{
                                background: showVolume ? 'rgba(38,166,154,0.1)' : 'transparent',
                                borderColor: showVolume ? 'rgba(38,166,154,0.3)' : (isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'),
                                color: showVolume ? '#26a69a' : 'var(--color-fg-muted)',
                            }}
                        >
                            <BarChart2 className="w-3.5 h-3.5" />
                            Volume
                        </button>
                    </div>
                </div>
            )}
            <div className="flex-1 flex flex-col min-w-0">
                <ChartToolbar
                    isDark={isDark}
                    sidebarOpen={sidebarOpen}
                    setSidebarOpen={setSidebarOpen}
                    chartType={chartType}
                    setChartType={setChartType}
                    magnetMode={magnetMode}
                    setMagnetMode={setMagnetMode}
                    symbol={symbol}
                    trend={trend}
                    crosshairData={crosshairData}
                    indicators={indicators}
                    drawings={drawings}
                    isAnyToolActive={isAnyToolActive}
                    activeTool={activeTool}
                    activeFibTool={activeFibTool}
                    cancelAllDrawing={cancelAllDrawing}
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
                </div>
                {hasRSI && (
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#f3f4f6' }}>
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
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#f3f4f6' }}>
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
                    <div className="border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
                        <div className="flex items-center justify-between px-3 py-1.5" style={{ background: isDark ? '#0a0a0c' : '#f3f4f6' }}>
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
            </div>
        </div>
    );
};

export default LightweightChart;
