import React from 'react';
import { useTranslation } from 'react-i18next';
import {
    TrendingUp, Minus, ArrowDownUp, Pencil,
    Type, Trash2, X, Star,
} from 'lucide-react';
import { useTheme } from '../../../shared/context/useTheme';
const DRAWING_TOOLS = [
    { id: 'TREND_LINE', labelKey: 'chart.drawingPanel.tools.trendline', Icon: TrendingUp, color: '#5E6AD2' },
    { id: 'HORIZONTAL_LINE', labelKey: 'chart.drawingPanel.tools.horizontal', Icon: Minus, color: '#f59e0b' },
    { id: 'VERTICAL_LINE', labelKey: 'chart.drawingPanel.tools.vertical', Icon: ArrowDownUp, color: '#06b6d4' },
    { id: 'FREEHAND', labelKey: 'chart.drawingPanel.tools.freehand', Icon: Pencil, color: '#10b981' },
    { id: 'TEXT', labelKey: 'chart.drawingPanel.tools.text', Icon: Type, color: '#8b5cf6' },
    { id: 'ICON', labelKey: 'chart.drawingPanel.tools.emoji', Icon: Star, color: '#f97316' },
];
const ICON_OPTIONS = [
    { id: '🚀', emoji: '🚀', labelKey: 'chart.drawingPanel.emojis.rocket' },
    { id: '📈', emoji: '📈', labelKey: 'chart.drawingPanel.emojis.trendUp' },
    { id: '📉', emoji: '📉', labelKey: 'chart.drawingPanel.emojis.trendDown' },
    { id: '💰', emoji: '💰', labelKey: 'chart.drawingPanel.emojis.money' },
    { id: '⚠️', emoji: '⚠️', labelKey: 'chart.drawingPanel.emojis.alert' },
    { id: '🔥', emoji: '🔥', labelKey: 'chart.drawingPanel.emojis.fire' },
    { id: '💎', emoji: '💎', labelKey: 'chart.drawingPanel.emojis.diamond' },
    { id: '🎯', emoji: '🎯', labelKey: 'chart.drawingPanel.emojis.target' },
    { id: '⭐', emoji: '⭐', labelKey: 'chart.drawingPanel.emojis.star' },
    { id: '🐂', emoji: '🐂', labelKey: 'chart.drawingPanel.emojis.bull' },
    { id: '🐻', emoji: '🐻', labelKey: 'chart.drawingPanel.emojis.bear' },
    { id: '🟢', emoji: '🟢', labelKey: 'chart.drawingPanel.emojis.green' },
    { id: '🔴', emoji: '🔴', labelKey: 'chart.drawingPanel.emojis.red' },
    { id: '📊', emoji: '📊', labelKey: 'chart.drawingPanel.emojis.chartUp' },
    { id: '🏦', emoji: '🏦', labelKey: 'chart.drawingPanel.emojis.bank' },
];
const DrawingPanel = ({
    activeTool,
    selectTool,
    cancelTool,
    drawings,
    removeDrawing,
    undoDrawing,
    clearDrawings,
    selectedIcon,
    setSelectedIcon,
    iconSize,
    setIconSize,
    onHighlight,
}) => {
    const { t } = useTranslation();
    const { isDark } = useTheme();
    return (
        <div className="space-y-2">
            { }
            <div className="grid grid-cols-3 gap-1">
                {DRAWING_TOOLS.map(({ id, labelKey, Icon, color }) => {
                    const isActive = activeTool === id;
                    return (
                        <button
                            key={id}
                            onClick={() => selectTool(id)}
                            className="flex flex-col items-center gap-1 px-2 py-2 rounded-lg text-[11px] font-medium border transition-all duration-150 cursor-pointer group"
                            style={{
                                background: isActive ? `${color}18` : 'transparent',
                                borderColor: isActive ? `${color}50` : 'var(--color-border-default)',
                                color: isActive ? color : 'var(--color-fg-muted)',
                                boxShadow: isActive ? `0 0 12px ${color}25` : 'none',
                            }}
                        >
                            <Icon
                                className="w-4 h-4 transition-all duration-150 group-hover:scale-110"
                                style={{
                                    color: isActive ? color : undefined,
                                    filter: isActive ? `drop-shadow(0 0 4px ${color}60)` : 'none',
                                }}
                            />
                            {t(labelKey)}
                        </button>
                    );
                })}
            </div>
            { }
            {activeTool === 'ICON' && (
                <div className="p-2 rounded-lg border space-y-2" style={{ background: isDark ? 'rgba(255,255,255,0.03)' : '#f8fafc', borderColor: isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0' }}>
                    <div>
                        <label className="block text-[10px] text-fg-muted uppercase tracking-wider font-medium mb-1.5">{t('chart.drawingPanel.selectIcon')}</label>
                        <div className="grid grid-cols-5 gap-1">
                            {ICON_OPTIONS.map(({ id, emoji, labelKey }) => (
                                <button
                                    key={id}
                                    onClick={() => setSelectedIcon(id)}
                                    className="w-8 h-8 flex items-center justify-center rounded-md border cursor-pointer group transition-all duration-150"
                                    style={{
                                        background: selectedIcon === id ? 'rgba(94,106,210,0.2)' : 'transparent',
                                        borderColor: selectedIcon === id ? 'rgba(94,106,210,0.4)' : (isDark ? 'rgba(255,255,255,0.06)' : '#e2e8f0'),
                                        boxShadow: selectedIcon === id ? '0 0 8px rgba(94,106,210,0.3)' : 'none',
                                    }}
                                    title={t(labelKey)}
                                >
                                    <span className="text-base transition-all duration-150 group-hover:scale-125" style={{ filter: selectedIcon === id ? 'drop-shadow(0 0 4px rgba(94,106,210,0.5))' : 'none' }}>
                                        {emoji}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>
                    <div>
                        <label className="block text-[10px] text-fg-muted uppercase tracking-wider font-medium mb-1">
                            {t('chart.drawingPanel.size')}: <span className="text-fg">{iconSize}px</span>
                        </label>
                        <input
                            type="range"
                            min="12"
                            max="64"
                            step="2"
                            value={iconSize}
                            onChange={e => setIconSize(Number(e.target.value))}
                            className="w-full h-1.5 rounded-full appearance-none cursor-pointer"
                            style={{ accentColor: '#f97316', background: isDark ? 'rgba(255,255,255,0.08)' : '#e2e8f0' }}
                        />
                    </div>
                </div>
            )}
            { }
            {activeTool && (
                <div className="flex items-center justify-between px-2.5 py-1.5 rounded-lg bg-[rgba(94,106,210,0.08)] border border-[rgba(94,106,210,0.15)]">
                    <span className="text-[11px] text-[#6872D9]">
                        {activeTool === 'FREEHAND' ? t('chart.drawingPanel.instructions.freehand') :
                            activeTool === 'TEXT' ? t('chart.drawingPanel.instructions.text') :
                                activeTool === 'ICON' ? t('chart.drawingPanel.instructions.emoji') :
                                    activeTool === 'HORIZONTAL_LINE' ? t('chart.drawingPanel.instructions.horizontal') :
                                        activeTool === 'VERTICAL_LINE' ? t('chart.drawingPanel.instructions.vertical') :
                                            t('chart.drawingPanel.instructions.twoPoint')}
                    </span>
                    <button
                        onClick={cancelTool}
                        className="p-0.5 border-none bg-transparent cursor-pointer text-fg-muted hover:text-fg transition-colors"
                    >
                        <X className="w-3 h-3" />
                    </button>
                </div>
            )}
            { }
            {drawings.length > 0 && (
                <div className="space-y-0.5 pt-1 border-t border-border-default">
                    <div className="flex items-center justify-between px-1 mb-1">
                        <span className="text-[10px] text-fg-muted uppercase tracking-wider font-medium">
                            {t('chart.drawingPanel.drawingsCount', { count: drawings.length })}
                        </span>
                        <div className="flex gap-1">
                            <button
                                onClick={undoDrawing}
                                className="text-[10px] text-fg-muted hover:text-fg bg-transparent border-none cursor-pointer px-1.5 py-0.5 rounded hover:bg-surface transition-colors"
                            >
                                {t('chart.drawingPanel.undo')}
                            </button>
                            <button
                                onClick={clearDrawings}
                                className="text-[10px] text-[#ef4444] hover:text-[#f87171] bg-transparent border-none cursor-pointer px-1.5 py-0.5 rounded hover:bg-[rgba(239,68,68,0.1)] transition-colors"
                            >
                                {t('chart.drawingPanel.clearAll')}
                            </button>
                        </div>
                    </div>
                    <div className="max-h-44 overflow-y-auto scrollbar-auto-hide space-y-0.5 pr-0.5">
                        {drawings.map((d, idx) => ({ d, idx })).reverse().map(({ d, idx }) => {
                            const tool = DRAWING_TOOLS.find(toolDef => toolDef.id === d.type);
                            const ToolIcon = tool?.Icon || TrendingUp;
                            return (
                                <button
                                    key={d.id}
                                    type="button"
                                    onClick={() => onHighlight?.(d.id)}
                                    className="group w-full flex items-center gap-2 px-2 py-1 rounded-md hover:bg-surface transition-colors border-none bg-transparent text-left cursor-pointer"
                                >
                                    <span className="text-[9px] font-mono text-fg-subtle tabular-nums w-5 text-right shrink-0">#{idx + 1}</span>
                                    <ToolIcon className="w-3 h-3 text-fg-muted group-hover:text-fg transition-colors shrink-0" style={{ color: tool?.color }} />
                                    <span className="text-[11px] text-fg-muted flex-1 truncate">
                                        {d.type === 'TEXT' ? `"${d.content}"` :
                                            d.type === 'ICON' ? d.iconId :
                                                (tool?.labelKey ? t(tool.labelKey) : d.type)}
                                    </span>
                                    <span
                                        role="button"
                                        tabIndex={0}
                                        onClick={(e) => { e.stopPropagation(); removeDrawing(d.id); }}
                                        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.stopPropagation(); removeDrawing(d.id); } }}
                                        className="opacity-0 group-hover:opacity-100 p-0.5 border-none bg-transparent cursor-pointer text-fg-muted hover:text-[#ef4444] transition-all"
                                    >
                                        <Trash2 className="w-3 h-3" />
                                    </span>
                                </button>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
};
export default DrawingPanel;
