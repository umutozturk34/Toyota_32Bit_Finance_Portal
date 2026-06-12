import React from 'react';
import { useTranslation } from 'react-i18next';
import { TOOL_LABEL_KEYS } from '../lib/drawingTools';
import {
    BarChart2, ChevronUp, ChevronDown, Diamond,
    LineChart, Layers, Crosshair,
    MousePointer2Off, Magnet, Maximize2, Minimize2, RotateCcw,
} from 'lucide-react';

const ChartToolbar = ({
    isDark, sidebarOpen, setSidebarOpen,
    chartType, setChartType,
    magnetMode, setMagnetMode,
    symbol, trend,
    isAnyToolActive, activeTool, activeFibTool,
    cancelAllDrawing,
    allowCandle = true,
    compareSymbol = null,
    isFullscreen = false,
    onToggleFullscreen = () => {},
    onResetView = () => {},
}) => {
    const { t } = useTranslation();
    const magnetLabel = t(`chart.toolbar.magnet.${magnetMode}`);
    const activeInstructionKey = activeTool === 'FREEHAND'
        ? 'chart.toolbar.activeInstruction.freehand'
        : (activeTool === 'TEXT' || activeTool === 'ICON')
            ? 'chart.toolbar.activeInstruction.click'
            : (activeTool === 'HORIZONTAL_LINE' || activeTool === 'VERTICAL_LINE')
                ? 'chart.toolbar.activeInstruction.click'
                : activeFibTool
                    ? (activeFibTool === 'EXTENSION'
                        ? 'chart.toolbar.activeInstruction.threePoint'
                        : 'chart.toolbar.activeInstruction.twoPoint')
                    : 'chart.toolbar.activeInstruction.dragDraw';
    return (
    <>
        <div className="flex items-stretch border-b border-border-default bg-surface/40 min-h-[44px]">
          <div className="flex items-center gap-2 sm:gap-3 px-2 sm:px-3 py-2 overflow-x-auto scrollbar-thin flex-1 min-w-0">
            <button
                onClick={() => setSidebarOpen(!sidebarOpen)}
                className="shrink-0 p-2 min-w-[40px] min-h-[40px] flex items-center justify-center rounded-md border-none cursor-pointer text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent"
                title={sidebarOpen ? t('chart.toolbar.hidePanel') : t('chart.toolbar.showPanel')}
            >
                <Layers className="w-4 h-4" />
            </button>
            <div className="shrink-0 flex items-center rounded-md border border-border-default overflow-hidden">
                <button
                    onClick={() => setChartType('line')}
                    className="flex items-center gap-1 px-2 py-1.5 min-h-[36px] text-[11px] font-medium border-none cursor-pointer transition-all duration-150"
                    style={{
                        background: chartType === 'line' ? 'rgba(94,106,210,0.15)' : 'transparent',
                        color: chartType === 'line' ? 'var(--color-accent)' : 'var(--color-fg-muted)',
                    }}
                    title={t('chart.toolbar.lineChart')}
                >
                    <LineChart className="w-3.5 h-3.5" />
                    <span className="hidden sm:inline">{t('chart.toolbar.line')}</span>
                </button>
                {allowCandle && (
                    <button
                        onClick={() => setChartType('candle')}
                        className="flex items-center gap-1 px-2 py-1.5 min-h-[36px] text-[11px] font-medium border-none cursor-pointer transition-all duration-150 border-l"
                        style={{
                            background: chartType === 'candle' ? 'rgba(94,106,210,0.15)' : 'transparent',
                            color: chartType === 'candle' ? 'var(--color-accent)' : 'var(--color-fg-muted)',
                            borderLeftColor: isDark ? 'rgba(255,255,255,0.08)' : '#e2e8f0',
                        }}
                        title={t('chart.toolbar.candleChart')}
                    >
                        <BarChart2 className="w-3.5 h-3.5" />
                        <span className="hidden sm:inline">{t('chart.toolbar.candle')}</span>
                    </button>
                )}
            </div>
            <button
                onClick={() => setMagnetMode(m => m === 'off' ? 'weak' : m === 'weak' ? 'strong' : 'off')}
                className="shrink-0 flex items-center gap-1 px-2 py-1.5 min-h-[36px] rounded-md text-[11px] font-medium border transition-all duration-150 cursor-pointer"
                style={{
                    background: magnetMode !== 'off' ? 'rgba(94,106,210,0.12)' : 'transparent',
                    borderColor: magnetMode !== 'off' ? 'rgba(94,106,210,0.3)' : 'var(--color-border-default)',
                    color: magnetMode !== 'off' ? 'var(--color-accent)' : 'var(--color-fg-muted)',
                }}
                title={`${t('chart.toolbar.magnetLabel')}: ${magnetLabel}`}
            >
                <Magnet className="w-3.5 h-3.5" />
                {magnetMode !== 'off' && (
                    <span className="font-semibold uppercase tracking-wider text-[10px]">
                        {magnetMode === 'weak' ? t('chart.toolbar.magnet.weakShort') : t('chart.toolbar.magnet.strongShort')}
                    </span>
                )}
            </button>
            <div className="shrink-0 w-px h-5 bg-border-default" />
            <div className="shrink-0 flex items-center gap-2">
                <span className="text-sm font-bold text-fg tracking-wide">{symbol?.toUpperCase()}</span>
                {compareSymbol && (
                    <span className="flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-full" style={{ background: 'rgba(239,68,68,0.12)', color: '#ef4444' }}>
                        <span className="w-2 h-0.5 rounded bg-[#ef4444]" />
                        {t('chart.toolbar.vs')} {compareSymbol.toUpperCase()}
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
                    {trend.direction === 'up' && <><ChevronUp className="w-3 h-3" /> {t('chart.toolbar.trend.up')}</>}
                    {trend.direction === 'down' && <><ChevronDown className="w-3 h-3" /> {t('chart.toolbar.trend.down')}</>}
                    {trend.direction === 'neutral' && <><Diamond className="w-3 h-3" /> {t('chart.toolbar.trend.neutral')}</>}
                    <span style={{ opacity: 0.7 }}>({trend.change > 0 ? '+' : ''}{trend.change.toFixed(2)}%)</span>
                </div>
            )}
          </div>
          <div className="shrink-0 flex items-center gap-1.5 px-1.5 sm:px-2 border-l border-border-default/60">
                {isAnyToolActive && (
                    <button
                        onClick={cancelAllDrawing}
                        className="flex items-center gap-1 px-2 py-1.5 min-h-[36px] rounded-md text-[11px] font-medium cursor-pointer transition-all duration-150 border"
                        style={{
                            background: 'rgba(239,68,68,0.08)',
                            borderColor: 'rgba(239,68,68,0.25)',
                            color: '#ef4444',
                        }}
                        title={t('chart.toolbar.exitDrawingMode')}
                    >
                        <MousePointer2Off className="w-3.5 h-3.5" />
                        <span className="hidden sm:inline">{t('chart.toolbar.exitDraw')}</span>
                    </button>
                )}
                <button
                    onClick={onResetView}
                    className="p-2 min-w-[40px] min-h-[40px] flex items-center justify-center rounded-md border-none cursor-pointer text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent"
                    title={t('chart.toolbar.resetView')}
                >
                    <RotateCcw className="w-4 h-4" />
                </button>
                <button
                    onClick={onToggleFullscreen}
                    className="p-2 min-w-[40px] min-h-[40px] flex items-center justify-center rounded-md border-none cursor-pointer text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent"
                    title={isFullscreen ? t('chart.toolbar.exitFullscreen') : t('chart.toolbar.fullscreen')}
                >
                    {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
                </button>
            </div>
        </div>
        {/* The overlay-indicator + OHLC readout now lives entirely in the on-chart top-left legend, so the old
            standalone indicator/drawings strip is gone — it duplicated SMA/EMA values and ate a whole row above
            the chart (worse on mobile). Drawings stay visible on the chart and in the "Çizimlerim" sidebar tab. */}
        {isAnyToolActive && (
            <div className="flex items-center justify-between px-3 py-1.5 bg-accent/10 border-b border-[rgba(94,106,210,0.15)]">
                <span className="flex items-center gap-1.5 text-[11px] text-accent">
                    <Crosshair className="w-3.5 h-3.5 text-accent" />
                    {t('chart.toolbar.active')}: <strong className="text-fg">{TOOL_LABEL_KEYS[activeTool || activeFibTool] ? t(TOOL_LABEL_KEYS[activeTool || activeFibTool]) : (activeTool || activeFibTool)}</strong>
                    <span className="text-fg-subtle">
                        {t(activeInstructionKey)}
                    </span>
                </span>
                <button
                    className="text-xs text-fg-muted hover:text-fg px-2 py-0.5 rounded hover:bg-surface transition-colors cursor-pointer bg-transparent border-none"
                    onClick={cancelAllDrawing}
                >
                    {t('chart.toolbar.cancel')}
                </button>
            </div>
        )}
    </>
    );
};

export default ChartToolbar;
