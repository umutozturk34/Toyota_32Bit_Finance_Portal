import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
    Plus, Trash2, Eye, EyeOff, Settings2, X,
} from 'lucide-react';
const INDICATOR_TYPES = [
    { value: 'SMA', label: 'SMA', defaultPeriod: 20, defaultColor: '#2196f3' },
    { value: 'EMA', label: 'EMA', defaultPeriod: 50, defaultColor: '#ff9800' },
    { value: 'RSI', label: 'RSI', defaultPeriod: 14, defaultColor: '#e91e63' },
    { value: 'MACD', label: 'MACD', defaultPeriod: 12, defaultColor: '#06b6d4' },
];
const COLOR_PRESETS = [
    '#2196f3', '#ff9800', '#8b5cf6', '#e91e63', '#10b981',
    '#f59e0b', '#ef4444', '#06b6d4', '#84cc16', '#f97316',
];
const IndicatorPanel = ({ indicators, addIndicator, removeIndicator, updateIndicator, toggleIndicator, allowedTypes }) => {
    const { t } = useTranslation();
    const availableTypes = allowedTypes
        ? INDICATOR_TYPES.filter(t => allowedTypes.includes(t.value))
        : INDICATOR_TYPES;
    const [showAddForm, setShowAddForm] = useState(false);
    const [newType, setNewType] = useState(availableTypes[0]?.value || 'SMA');
    const [newPeriod, setNewPeriod] = useState(20);
    const [newColor, setNewColor] = useState('#2196f3');
    const [editingId, setEditingId] = useState(null);
    const handleAddType = (type) => {
        const t = INDICATOR_TYPES.find(i => i.value === type);
        setNewType(type);
        setNewPeriod(t?.defaultPeriod || 20);
        setNewColor(t?.defaultColor || '#2196f3');
    };
    const handleAdd = () => {
        addIndicator(newType, newPeriod, newColor);
        setShowAddForm(false);
    };
    return (
        <div className="space-y-2">
            {}
            {indicators.length > 0 && (
                <div className="space-y-1">
                    {indicators.map(ind => (
                        <div
                            key={ind.id}
                            className="group flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-surface/50 hover:bg-surface transition-colors duration-150 min-w-0"
                        >
                            <button
                                onClick={() => toggleIndicator(ind.id)}
                                className="shrink-0 p-0 border-none bg-transparent cursor-pointer text-fg-muted hover:text-fg transition-colors"
                                title={ind.visible ? t('chart.indicators.hide') : t('chart.indicators.show')}
                            >
                                {ind.visible
                                    ? <Eye className="w-3.5 h-3.5" />
                                    : <EyeOff className="w-3.5 h-3.5 opacity-50" />
                                }
                            </button>
                            <span
                                className="w-2.5 h-2.5 rounded-full shrink-0 ring-1 ring-white/10"
                                style={{ background: ind.color }}
                            />
                            <span className={`text-xs font-semibold tracking-wide truncate min-w-0 ${ind.visible ? 'text-fg' : 'text-fg-muted line-through opacity-50'}`}>
                                {ind.type}
                            </span>
                            {}
                            {editingId === ind.id ? (
                                <div className="flex items-center gap-1.5 ml-auto">
                                    <input
                                        type="number"
                                        min={1}
                                        max={500}
                                        value={ind.period}
                                        onChange={e => updateIndicator(ind.id, { period: parseInt(e.target.value) || 1 })}
                                        className="w-14 px-1.5 py-0.5 rounded bg-bg-base border border-border-default text-xs text-fg outline-none focus:border-accent"
                                    />
                                    <div className="flex gap-0.5">
                                        {COLOR_PRESETS.slice(0, 5).map(c => (
                                            <button
                                                key={c}
                                                onClick={() => updateIndicator(ind.id, { color: c })}
                                                className="w-4 h-4 rounded-full border-none cursor-pointer hover:scale-125 transition-transform"
                                                style={{
                                                    background: c,
                                                    outline: ind.color === c ? '2px solid var(--color-accent)' : 'none',
                                                    outlineOffset: '1px',
                                                }}
                                            />
                                        ))}
                                    </div>
                                    <button
                                        onClick={() => setEditingId(null)}
                                        className="p-0.5 border-none bg-transparent cursor-pointer text-fg-muted hover:text-fg"
                                    >
                                        <X className="w-3 h-3" />
                                    </button>
                                </div>
                            ) : (
                                <>
                                    <span className="text-[11px] text-fg-muted font-mono shrink-0">{ind.period}</span>
                                    <div className="flex items-center gap-0.5 ml-auto shrink-0 opacity-60 group-hover:opacity-100 transition-opacity">
                                        <button
                                            onClick={() => setEditingId(ind.id)}
                                            className="p-1 border-none bg-transparent cursor-pointer text-fg-muted hover:text-fg rounded hover:bg-surface transition-colors"
                                            title={t('chart.indicators.edit')}
                                        >
                                            <Settings2 className="w-3 h-3" />
                                        </button>
                                        <button
                                            onClick={() => removeIndicator(ind.id)}
                                            className="p-1 border-none bg-transparent cursor-pointer text-fg-muted hover:text-[#ef4444] rounded hover:bg-[rgba(239,68,68,0.1)] transition-colors"
                                            title={t('chart.indicators.remove')}
                                        >
                                            <Trash2 className="w-3 h-3" />
                                        </button>
                                    </div>
                                </>
                            )}
                        </div>
                    ))}
                </div>
            )}
            {}
            {showAddForm ? (
                <div className="p-2.5 rounded-lg bg-surface/50 border border-border-default space-y-2.5">
                    <div className="flex gap-1">
                        {availableTypes.map(t => (
                            <button
                                key={t.value}
                                onClick={() => handleAddType(t.value)}
                                className={`flex-1 px-2 py-1.5 rounded-md text-xs font-semibold border transition-all duration-150 cursor-pointer ${newType === t.value
                                    ? 'bg-accent text-white border-accent'
                                    : 'bg-transparent text-fg-muted border-border-default hover:text-fg hover:border-fg-subtle'
                                    }`}
                            >
                                {t.label}
                            </button>
                        ))}
                    </div>
                    <div className="flex items-center gap-2">
                        <label className="text-[10px] text-fg-muted uppercase tracking-wider font-medium">{t('chart.indicators.period')}</label>
                        <input
                            type="number"
                            min={1}
                            max={500}
                            value={newPeriod}
                            onChange={e => setNewPeriod(parseInt(e.target.value) || 1)}
                            className="w-16 px-2 py-1 rounded-md bg-bg-base border border-border-default text-xs text-fg outline-none focus:border-accent"
                        />
                    </div>
                    <div className="flex items-center gap-1.5">
                        <label className="text-[10px] text-fg-muted uppercase tracking-wider font-medium mr-1">{t('chart.indicators.color')}</label>
                        {COLOR_PRESETS.map(c => (
                            <button
                                key={c}
                                onClick={() => setNewColor(c)}
                                className="w-5 h-5 rounded-full border-none cursor-pointer hover:scale-110 transition-transform"
                                style={{
                                    background: c,
                                    outline: newColor === c ? '2px solid var(--color-accent)' : 'none',
                                    outlineOffset: '1px',
                                }}
                            />
                        ))}
                    </div>
                    <div className="flex gap-1.5">
                        <button
                            onClick={handleAdd}
                            className="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-md bg-accent text-white text-xs font-semibold border-none cursor-pointer hover:bg-accent-bright transition-colors"
                        >
                            <Plus className="w-3 h-3" /> {t('chart.indicators.add')}
                        </button>
                        <button
                            onClick={() => setShowAddForm(false)}
                            className="px-3 py-1.5 rounded-md bg-transparent text-fg-muted text-xs font-medium border border-border-default cursor-pointer hover:text-fg hover:border-fg-subtle transition-colors"
                        >
                            {t('chart.indicators.cancel')}
                        </button>
                    </div>
                </div>
            ) : (
                <button
                    onClick={() => setShowAddForm(true)}
                    className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-fg-muted bg-transparent border border-dashed border-border-default cursor-pointer hover:text-fg hover:border-fg-subtle hover:bg-surface/50 transition-all duration-150"
                >
                    <Plus className="w-3 h-3" /> {t('chart.indicators.addIndicator')}
                </button>
            )}
        </div>
    );
};
export default IndicatorPanel;
