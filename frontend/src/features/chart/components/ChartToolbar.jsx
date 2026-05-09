import React from 'react';
import {
    BarChart2, ChevronUp, ChevronDown, Diamond,
    LineChart, Layers, Crosshair,
    MousePointer2Off, Magnet, Maximize2, Minimize2,
} from 'lucide-react';

const ChartToolbar = ({
    isDark, sidebarOpen, setSidebarOpen,
    chartType, setChartType,
    magnetMode, setMagnetMode,
    symbol, trend, crosshairData,
    indicators, drawings,
    isAnyToolActive, activeTool, activeFibTool,
    cancelAllDrawing,
    allowCandle = true,
    compareSymbol = null,
    isFullscreen = false,
    onToggleFullscreen = () => {},
}) => (
    <>
        <div className="flex items-center gap-3 px-3 py-2 border-b border-border-default bg-surface/40">
            <button
                onClick={() => setSidebarOpen(!sidebarOpen)}
                className="p-1.5 rounded-md border-none cursor-pointer text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent"
                title={sidebarOpen ? 'Hide panel' : 'Show panel'}
            >
                <Layers className="w-4 h-4" />
            </button>
            <div className="flex items-center rounded-md border border-border-default overflow-hidden">
                <button
                    onClick={() => setChartType('line')}
                    className="flex items-center gap-1 px-2 py-1 text-[11px] font-medium border-none cursor-pointer transition-all duration-150"
                    style={{
                        background: chartType === 'line' ? 'rgba(94,106,210,0.15)' : 'transparent',
                        color: chartType === 'line' ? '#6872D9' : 'var(--color-fg-muted)',
                    }}
                    title="Line chart"
                >
                    <LineChart className="w-3.5 h-3.5" />
                    Line
                </button>
                {allowCandle && (
                    <button
                        onClick={() => setChartType('candle')}
                        className="flex items-center gap-1 px-2 py-1 text-[11px] font-medium border-none cursor-pointer transition-all duration-150 border-l"
                        style={{
                            background: chartType === 'candle' ? 'rgba(94,106,210,0.15)' : 'transparent',
                            color: chartType === 'candle' ? '#6872D9' : 'var(--color-fg-muted)',
                            borderLeftColor: isDark ? 'rgba(255,255,255,0.08)' : '#e2e8f0',
                        }}
                        title="Candlestick chart"
                    >
                        <BarChart2 className="w-3.5 h-3.5" />
                        Candle
                    </button>
                )}
            </div>
            <button
                onClick={() => setMagnetMode(m => m === 'off' ? 'weak' : m === 'weak' ? 'strong' : 'off')}
                className="flex items-center gap-1 px-2 py-1 rounded-md text-[11px] font-medium border transition-all duration-150 cursor-pointer"
                style={{
                    background: magnetMode !== 'off' ? 'rgba(94,106,210,0.12)' : 'transparent',
                    borderColor: magnetMode !== 'off' ? 'rgba(94,106,210,0.3)' : 'var(--color-border-default)',
                    color: magnetMode !== 'off' ? '#5E6AD2' : 'var(--color-fg-muted)',
                }}
                title={`Magnet: ${magnetMode === 'off' ? 'Off' : magnetMode === 'weak' ? 'Weak (High/Low)' : 'Strong (OHLC)'}`}
            >
                <Magnet className="w-3.5 h-3.5" />
                {magnetMode !== 'off' && (
                    <span className="font-semibold uppercase tracking-wider text-[10px]">
                        {magnetMode === 'weak' ? 'W' : 'S'}
                    </span>
                )}
            </button>
            <div className="w-px h-5 bg-border-default" />
            <div className="flex items-center gap-2">
                <span className="text-sm font-bold text-fg tracking-wide">{symbol?.toUpperCase()}</span>
                {compareSymbol && (
                    <span className="flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-full" style={{ background: 'rgba(239,68,68,0.12)', color: '#ef4444' }}>
                        <span className="w-2 h-0.5 rounded bg-[#ef4444]" />
                        vs {compareSymbol.toUpperCase()}
                        <span className="text-[10px] font-mono opacity-70">(%)</span>
                    </span>
                )}
            </div>
            {trend && (
                <div
                    className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold"
                    style={{
                        background: trend.direction === 'up' ? 'rgba(16,185,129,0.12)' : trend.direction === 'down' ? 'rgba(239,68,68,0.12)' : 'rgba(245,158,11,0.12)',
                        color: trend.direction === 'up' ? '#10b981' : trend.direction === 'down' ? '#ef4444' : '#f59e0b',
                    }}
                >
                    {trend.direction === 'up' && <><ChevronUp className="w-3 h-3" /> Uptrend</>}
                    {trend.direction === 'down' && <><ChevronDown className="w-3 h-3" /> Downtrend</>}
                    {trend.direction === 'neutral' && <><Diamond className="w-3 h-3" /> Sideways</>}
                    <span style={{ opacity: 0.7 }}>({trend.change > 0 ? '+' : ''}{trend.change.toFixed(2)}%)</span>
                </div>
            )}
            {crosshairData && crosshairData.open != null && (
                <div className="flex items-center gap-3 text-[11px] font-mono">
                    <span className="text-fg-muted">O <span className="text-fg">{Number(crosshairData.open).toFixed(2)}</span></span>
                    <span className="text-fg-muted">H <span className="text-[#10b981]">{Number(crosshairData.high).toFixed(2)}</span></span>
                    <span className="text-fg-muted">L <span className="text-[#ef4444]">{Number(crosshairData.low).toFixed(2)}</span></span>
                    <span className="text-fg-muted">C <span style={{ color: Number(crosshairData.close) >= Number(crosshairData.open) ? '#10b981' : '#ef4444' }}>{Number(crosshairData.close).toFixed(2)}</span></span>
                </div>
            )}
            <div className="flex items-center gap-2.5 ml-auto">
                {indicators.filter(i => i.visible && i.type !== 'RSI').map(ind => (
                    <span key={ind.id} className="flex items-center gap-1 text-[11px] font-medium" style={{ color: ind.color }}>
                        <span className="w-1.5 h-1.5 rounded-full" style={{ background: ind.color }} />
                        {ind.type} {ind.period}
                    </span>
                ))}
                {drawings.length > 0 && (
                    <span className="text-[11px] text-fg-subtle font-medium px-2 py-0.5 rounded-full bg-surface border border-border-default">
                        {drawings.length} drawing{drawings.length > 1 ? 's' : ''}
                    </span>
                )}
                {isAnyToolActive && (
                    <button
                        onClick={cancelAllDrawing}
                        className="flex items-center gap-1 px-2 py-1 rounded-md text-[11px] font-medium cursor-pointer transition-all duration-150 border"
                        style={{
                            background: 'rgba(239,68,68,0.08)',
                            borderColor: 'rgba(239,68,68,0.25)',
                            color: '#ef4444',
                        }}
                        title="Exit drawing mode"
                    >
                        <MousePointer2Off className="w-3.5 h-3.5" />
                        Exit Draw
                    </button>
                )}
                <button
                    onClick={onToggleFullscreen}
                    className="p-1.5 rounded-md border-none cursor-pointer text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent"
                    title={isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
                >
                    {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
                </button>
            </div>
        </div>
        {isAnyToolActive && (
            <div className="flex items-center justify-between px-3 py-1.5 bg-[rgba(94,106,210,0.08)] border-b border-[rgba(94,106,210,0.15)]">
                <span className="flex items-center gap-1.5 text-[11px] text-[#6872D9]">
                    <Crosshair className="w-3.5 h-3.5 text-[#5E6AD2]" />
                    Active: <strong className="text-fg">{activeTool || activeFibTool}</strong>
                    <span className="text-fg-subtle">
                        {activeTool === 'FREEHAND' ? '— Click & drag to draw' :
                            activeTool === 'TEXT' || activeTool === 'ICON' ? '— Click to place' :
                                activeTool === 'HORIZONTAL_LINE' || activeTool === 'VERTICAL_LINE' ? '— Click to place' :
                                    activeFibTool ? (activeFibTool === 'EXTENSION' ? '— Click 3 points' : '— Click 2 points') :
                                        '— Click & drag'}
                    </span>
                </span>
                <button
                    className="text-xs text-fg-muted hover:text-fg px-2 py-0.5 rounded hover:bg-surface transition-colors cursor-pointer bg-transparent border-none"
                    onClick={cancelAllDrawing}
                >
                    Cancel
                </button>
            </div>
        )}
    </>
);

export default ChartToolbar;
