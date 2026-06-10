import React from 'react';
import { useTranslation } from 'react-i18next';
import { TrendingUp, Trash2, PenLine } from 'lucide-react';
import { DRAWING_TOOLS, FIB_TOOLS } from '../lib/drawingTools';

const TOOL_BY_ID = Object.fromEntries(DRAWING_TOOLS.map((tool) => [tool.id, tool]));
const FIB_BY_ID = Object.fromEntries(FIB_TOOLS.map((tool) => [tool.id, tool]));

// "Çizimlerim" — saved drawings AND fibonacci tools, list ONLY. The pick-and-use tools (incl. fib) live on the
// left rail; this collapsible tab lists / highlights / recolors / deletes them so the tool picker isn't duplicated.
const DrawingPanel = ({
    drawings = [],
    removeDrawing,
    updateDrawing,
    undoDrawing,
    clearDrawings,
    onHighlight,
    fibTools = [],
    removeFibTool,
    clearFibTools,
    onHighlightFib,
}) => {
    const { t } = useTranslation();
    const total = drawings.length + fibTools.length;

    if (total === 0) {
        return (
            <div className="flex flex-col items-center justify-center gap-2 py-10 text-center">
                <PenLine className="w-6 h-6 text-fg-subtle" />
                <p className="px-4 text-[11px] leading-snug text-fg-muted">{t('chart.drawingPanel.empty')}</p>
            </div>
        );
    }

    return (
        <div className="space-y-0.5">
            <div className="flex items-center justify-between px-1 mb-1">
                <span className="text-[10px] text-fg-muted uppercase tracking-wider font-medium">
                    {t('chart.drawingPanel.drawingsCount', { count: total })}
                </span>
                <div className="flex gap-1">
                    {drawings.length > 0 && (
                        <button
                            onClick={undoDrawing}
                            className="text-[10px] text-fg-muted hover:text-fg bg-transparent border-none cursor-pointer px-1.5 py-0.5 rounded hover:bg-surface transition-colors"
                        >
                            {t('chart.drawingPanel.undo')}
                        </button>
                    )}
                    <button
                        onClick={() => { clearDrawings?.(); clearFibTools?.(); }}
                        className="text-[10px] text-danger hover:text-danger bg-transparent border-none cursor-pointer px-1.5 py-0.5 rounded hover:bg-danger/10 transition-colors"
                    >
                        {t('chart.drawingPanel.clearAll')}
                    </button>
                </div>
            </div>
            <div className="max-h-[60dvh] lg:max-h-[28rem] overflow-y-auto overscroll-contain scrollbar-auto-hide space-y-0.5 pr-0.5">
                {drawings.map((d, idx) => ({ d, idx })).reverse().map(({ d, idx }) => {
                    const tool = TOOL_BY_ID[d.type];
                    const ToolIcon = tool?.Icon || TrendingUp;
                    const label = d.type === 'TEXT' ? `"${d.content}"`
                        : d.type === 'ICON' ? d.iconId
                            : (tool?.labelKey ? t(tool.labelKey) : d.type);
                    return (
                        <div
                            key={d.id}
                            className="group w-full flex items-center gap-1.5 px-1.5 py-1 rounded-md hover:bg-surface transition-colors"
                        >
                            <button
                                type="button"
                                onClick={() => onHighlight?.(d.id)}
                                className="flex items-center gap-2 flex-1 min-w-0 border-none bg-transparent text-left cursor-pointer"
                            >
                                <span className="text-[9px] font-mono text-fg-subtle tabular-nums w-5 text-right shrink-0">#{idx + 1}</span>
                                <ToolIcon className="w-3 h-3 shrink-0" style={{ color: d.color || tool?.color }} />
                                <span className="text-[11px] text-fg-muted flex-1 truncate">{label}</span>
                            </button>
                            {d.type !== 'ICON' && d.type !== 'TEXT' && (
                                <label
                                    className="relative inline-flex h-5 w-5 shrink-0 items-center justify-center cursor-pointer overflow-hidden"
                                    title={t('chart.toolRail.color')}
                                >
                                    <span className="h-3.5 w-3.5 rounded-full border border-border-strong" style={{ background: d.color || tool?.color }} />
                                    <input
                                        type="color"
                                        value={d.color || tool?.color || '#5E6AD2'}
                                        onChange={(e) => updateDrawing?.(d.id, { color: e.target.value })}
                                        className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
                                    />
                                </label>
                            )}
                            <button
                                type="button"
                                onClick={() => removeDrawing?.(d.id)}
                                title={t('chart.drawingPanel.clearAll')}
                                className="p-1 border-none bg-transparent cursor-pointer text-fg-muted hover:text-danger transition-colors shrink-0"
                            >
                                <Trash2 className="w-3.5 h-3.5" />
                            </button>
                        </div>
                    );
                })}
                {fibTools.map((f) => {
                    const tool = FIB_BY_ID[f.type];
                    const FibIcon = tool?.Icon || TrendingUp;
                    return (
                        <div
                            key={f.id}
                            className="group w-full flex items-center gap-1.5 px-1.5 py-1 rounded-md hover:bg-surface transition-colors"
                        >
                            <button
                                type="button"
                                onClick={() => onHighlightFib?.(f.id)}
                                className="flex items-center gap-2 flex-1 min-w-0 border-none bg-transparent text-left cursor-pointer"
                            >
                                <span className="text-[8px] font-mono font-bold text-fg-subtle w-5 text-right shrink-0">FIB</span>
                                <FibIcon className="w-3 h-3 shrink-0" style={{ color: f.color || tool?.color }} />
                                <span className="text-[11px] text-fg-muted flex-1 truncate">{tool?.labelKey ? t(tool.labelKey) : f.type}</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => removeFibTool?.(f.id)}
                                title={t('chart.drawingPanel.clearAll')}
                                className="p-1 border-none bg-transparent cursor-pointer text-fg-muted hover:text-danger transition-colors shrink-0"
                            >
                                <Trash2 className="w-3.5 h-3.5" />
                            </button>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};
export default DrawingPanel;
