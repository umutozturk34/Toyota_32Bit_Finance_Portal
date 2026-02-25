import React from 'react';
import {
    TrendingUp, Minus, ArrowDownUp, Pencil,
    Type, Trash2, X, Star,
} from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
const DRAWING_TOOLS = [
    { id: 'TREND_LINE', label: 'Trend', Icon: TrendingUp, color: '#5E6AD2' },
    { id: 'HORIZONTAL_LINE', label: 'H-Line', Icon: Minus, color: '#f59e0b' },
    { id: 'VERTICAL_LINE', label: 'V-Line', Icon: ArrowDownUp, color: '#06b6d4' },
    { id: 'FREEHAND', label: 'Free', Icon: Pencil, color: '#10b981' },
    { id: 'TEXT', label: 'Text', Icon: Type, color: '#8b5cf6' },
    { id: 'ICON', label: 'Emoji', Icon: Star, color: '#f97316' },
];
const ICON_OPTIONS = [
    { id: '🚀', emoji: '🚀', label: 'Rocket' },
    { id: '📈', emoji: '📈', label: 'Trend Up' },
    { id: '📉', emoji: '📉', label: 'Trend Down' },
    { id: '💰', emoji: '💰', label: 'Money' },
    { id: '⚠️', emoji: '⚠️', label: 'Alert' },
    { id: '🔥', emoji: '🔥', label: 'Fire' },
    { id: '💎', emoji: '💎', label: 'Diamond' },
    { id: '🎯', emoji: '🎯', label: 'Target' },
    { id: '⭐', emoji: '⭐', label: 'Star' },
    { id: '🐂', emoji: '🐂', label: 'Bull' },
    { id: '🐻', emoji: '🐻', label: 'Bear' },
    { id: '🟢', emoji: '🟢', label: 'Green' },
    { id: '🔴', emoji: '🔴', label: 'Red' },
    { id: '📊', emoji: '📊', label: 'Chart' },
    { id: '🏦', emoji: '🏦', label: 'Bank' },
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
    textInput,
    setTextInput,
    textSize,
    setTextSize,
}) => {
    const { isDark } = useTheme();
    return (
        <div className="space-y-2">
            {}
            <div className="grid grid-cols-3 gap-1">
                {DRAWING_TOOLS.map(({ id, label, Icon, color }) => {
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
                            {label}
                        </button>
                    );
                })}
            </div>
            {}
            {activeTool === 'TEXT' && (
                <div className="p-2 rounded-lg border space-y-2" style={{ background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)', borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)' }}>
                    <div>
                        <label className="block text-[10px] text-fg-muted uppercase tracking-wider font-medium mb-1">Text Content</label>
                        <input
                            type="text"
                            value={textInput}
                            onChange={e => setTextInput(e.target.value)}
                            placeholder="Type text..."
                            className="w-full px-2.5 py-1.5 rounded-md border text-xs outline-none focus:border-accent"
                            style={{ background: isDark ? '#0F0F12' : '#f3f4f6', borderColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.12)', color: isDark ? '#EDEDEF' : '#1a1a2e' }}
                        />
                    </div>
                    <div>
                        <label className="block text-[10px] text-fg-muted uppercase tracking-wider font-medium mb-1">
                            Font Size: <span className="text-fg">{textSize}px</span>
                        </label>
                        <input
                            type="range"
                            min="10"
                            max="28"
                            step="1"
                            value={textSize}
                            onChange={e => setTextSize(Number(e.target.value))}
                            className="w-full h-1.5 rounded-full appearance-none cursor-pointer"
                            style={{ accentColor: '#5E6AD2', background: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)' }}
                        />
                    </div>
                </div>
            )}
            {}
            {activeTool === 'ICON' && (
                <div className="p-2 rounded-lg border" style={{ background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)', borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)' }}>
                    <label className="block text-[10px] text-fg-muted uppercase tracking-wider font-medium mb-1.5">Select Icon</label>
                    <div className="grid grid-cols-5 gap-1">
                        {ICON_OPTIONS.map(({ id, emoji, label }) => (
                            <button
                                key={id}
                                onClick={() => setSelectedIcon(id)}
                                className="w-8 h-8 flex items-center justify-center rounded-md border cursor-pointer group transition-all duration-150"
                                style={{
                                    background: selectedIcon === id ? 'rgba(94,106,210,0.2)' : 'transparent',
                                    borderColor: selectedIcon === id ? 'rgba(94,106,210,0.4)' : (isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'),
                                    boxShadow: selectedIcon === id ? '0 0 8px rgba(94,106,210,0.3)' : 'none',
                                }}
                                title={label}
                            >
                                <span className="text-base transition-all duration-150 group-hover:scale-125" style={{ filter: selectedIcon === id ? 'drop-shadow(0 0 4px rgba(94,106,210,0.5))' : 'none' }}>
                                    {emoji}
                                </span>
                            </button>
                        ))}
                    </div>
                </div>
            )}
            {}
            {activeTool && (
                <div className="flex items-center justify-between px-2.5 py-1.5 rounded-lg bg-[rgba(94,106,210,0.08)] border border-[rgba(94,106,210,0.15)]">
                    <span className="text-[11px] text-[#6872D9]">
                        {activeTool === 'FREEHAND' ? 'Click & drag to draw' :
                            activeTool === 'TEXT' ? 'Click to place text' :
                                activeTool === 'ICON' ? 'Click to place icon' :
                                    activeTool === 'HORIZONTAL_LINE' ? 'Click to place line' :
                                        activeTool === 'VERTICAL_LINE' ? 'Click to place line' :
                                            'Click two points'}
                    </span>
                    <button
                        onClick={cancelTool}
                        className="p-0.5 border-none bg-transparent cursor-pointer text-fg-muted hover:text-fg transition-colors"
                    >
                        <X className="w-3 h-3" />
                    </button>
                </div>
            )}
            {}
            {drawings.length > 0 && (
                <div className="space-y-0.5 pt-1 border-t border-border-default">
                    <div className="flex items-center justify-between px-1 mb-1">
                        <span className="text-[10px] text-fg-muted uppercase tracking-wider font-medium">
                            Drawings ({drawings.length})
                        </span>
                        <div className="flex gap-1">
                            <button
                                onClick={undoDrawing}
                                className="text-[10px] text-fg-muted hover:text-fg bg-transparent border-none cursor-pointer px-1.5 py-0.5 rounded hover:bg-surface transition-colors"
                            >
                                Undo
                            </button>
                            <button
                                onClick={clearDrawings}
                                className="text-[10px] text-[#ef4444] hover:text-[#f87171] bg-transparent border-none cursor-pointer px-1.5 py-0.5 rounded hover:bg-[rgba(239,68,68,0.1)] transition-colors"
                            >
                                Clear All
                            </button>
                        </div>
                    </div>
                    {drawings.slice(-6).reverse().map(d => {
                        const tool = DRAWING_TOOLS.find(t => t.id === d.type);
                        const ToolIcon = tool?.Icon || TrendingUp;
                        return (
                            <div
                                key={d.id}
                                className="group flex items-center gap-2 px-2 py-1 rounded-md hover:bg-surface transition-colors"
                            >
                                <ToolIcon className="w-3 h-3 text-fg-muted group-hover:text-fg transition-colors" />
                                <span className="text-[11px] text-fg-muted flex-1 truncate">
                                    {d.type === 'TEXT' ? `"${d.content}"` :
                                        d.type === 'ICON' ? d.iconId :
                                            tool?.label || d.type}
                                </span>
                                <button
                                    onClick={() => removeDrawing(d.id)}
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
export { ICON_OPTIONS };
export default DrawingPanel;
