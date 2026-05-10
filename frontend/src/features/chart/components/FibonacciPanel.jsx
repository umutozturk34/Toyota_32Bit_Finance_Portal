import React from 'react';
import { useTranslation } from 'react-i18next';
import { Triangle, GitBranch, Trash2 } from 'lucide-react';
const FIB_TOOL_TYPES = [
    { id: 'RETRACEMENT', labelKey: 'chart.fibonacci.tools.retracement', Icon: Triangle, color: '#ec4899' },
    { id: 'EXTENSION', labelKey: 'chart.fibonacci.tools.extension', Icon: GitBranch, color: '#f97316' },
];
const FibonacciPanel = ({
    activeFibTool,
    selectFibTool,
    cancelFibTool,
    fibTools,
    removeFibTool,
    clearFibTools,
}) => {
    const { t } = useTranslation();
    return (
        <div className="space-y-1.5">
            {}
            <div className="space-y-1">
                {FIB_TOOL_TYPES.map(({ id, labelKey, Icon, color }) => {
                    const isActive = activeFibTool === id;
                    return (
                        <button
                            key={id}
                            onClick={() => selectFibTool(id)}
                            className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all duration-150 cursor-pointer"
                            style={{
                                background: isActive ? `${color}18` : 'transparent',
                                borderColor: isActive ? `${color}50` : 'var(--color-border-default)',
                                color: isActive ? color : 'var(--color-fg-muted)',
                                boxShadow: isActive ? `0 0 8px ${color}20` : 'none',
                            }}
                        >
                            <Icon className="w-3.5 h-3.5" />
                            {t(labelKey)}
                        </button>
                    );
                })}
            </div>
            {}
            {activeFibTool && (
                <div className="flex items-center justify-between px-2 py-1 rounded-md bg-[rgba(236,72,153,0.08)] border border-[rgba(236,72,153,0.15)]">
                    <span className="text-[10px] text-[#ec4899]">
                        {activeFibTool === 'EXTENSION' ? t('chart.fibonacci.instructions.threePoint') : t('chart.fibonacci.instructions.twoPoint')}
                    </span>
                    <button
                        onClick={cancelFibTool}
                        className="text-[10px] text-fg-muted hover:text-fg bg-transparent border-none cursor-pointer px-1 py-0.5 rounded hover:bg-surface transition-colors"
                    >
                        {t('chart.fibonacci.cancel')}
                    </button>
                </div>
            )}
            {}
            <div className="flex flex-wrap gap-x-2 gap-y-0 px-1">
                {[0, 0.236, 0.382, 0.5, 0.618, 0.786, 1, 1.618].map(level => (
                    <span
                        key={level}
                        className="text-[9px] font-mono"
                        style={{
                            color: level === 0.5 || level === 0.618
                                ? '#ec4899'
                                : 'var(--color-fg-subtle)',
                        }}
                    >
                        {(level * 100).toFixed(level === 0 || level === 0.5 || level === 1 ? 0 : 1)}%
                    </span>
                ))}
            </div>
            {}
            {fibTools.length > 0 && (
                <div className="space-y-0.5 pt-1 border-t border-border-default">
                    <div className="flex items-center justify-between px-1">
                        <span className="text-[10px] text-fg-muted uppercase tracking-wider font-medium">
                            {t('chart.fibonacci.active', { count: fibTools.length })}
                        </span>
                        <button
                            onClick={clearFibTools}
                            className="text-[10px] text-[#ef4444] hover:text-[#f87171] bg-transparent border-none cursor-pointer px-1 py-0.5 rounded hover:bg-[rgba(239,68,68,0.1)] transition-colors"
                        >
                            {t('chart.fibonacci.clear')}
                        </button>
                    </div>
                    {fibTools.map(f => {
                        const tool = FIB_TOOL_TYPES.find(toolDef => toolDef.id === f.type);
                        const ToolIcon = tool?.Icon || Triangle;
                        return (
                            <div
                                key={f.id}
                                className="group flex items-center gap-2 px-2 py-0.5 rounded-md hover:bg-surface transition-colors"
                            >
                                <ToolIcon className="w-3 h-3" style={{ color: tool?.color }} />
                                <span className="text-[11px] text-fg-muted flex-1">{tool?.labelKey ? t(tool.labelKey) : ''}</span>
                                <button
                                    onClick={() => removeFibTool(f.id)}
                                    className="opacity-0 group-hover:opacity-100 p-0.5 border-none bg-transparent cursor-pointer text-fg-muted hover:text-[#ef4444] transition-all"
                                >
                                    <Trash2 className="w-3 h-3" />
                                </button>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
};
export default FibonacciPanel;
