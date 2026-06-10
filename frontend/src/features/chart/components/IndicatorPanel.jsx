import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
    Plus, Trash2, Eye, EyeOff, Settings2, X, AlertCircle, Check,
} from 'lucide-react';
import { MAX_INDICATORS } from '../hooks/useIndicators';
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
// Period inputs accept free typing (including a momentarily empty field) and clamp to [1,500] only on blur, so
// you can clear the box and type a fresh value instead of it snapping to 1 on every keystroke.
const clampPeriod = (v) => Math.min(500, Math.max(1, parseInt(v, 10) || 1));
const digitsOnly = (v) => v.replace(/[^0-9]/g, '');
const IndicatorPanel = ({ indicators, addIndicator, removeIndicator, updateIndicator, toggleIndicator, allowedTypes }) => {
    const { t } = useTranslation();
    const availableTypes = allowedTypes
        ? INDICATOR_TYPES.filter(t => allowedTypes.includes(t.value))
        : INDICATOR_TYPES;
    const [showAddForm, setShowAddForm] = useState(false);
    const [newType, setNewType] = useState(availableTypes[0]?.value || 'SMA');
    const [newPeriod, setNewPeriod] = useState('20');
    const [newColor, setNewColor] = useState('#2196f3');
    const [editingId, setEditingId] = useState(null);
    const [editPeriod, setEditPeriod] = useState('');
    const [addError, setAddError] = useState(null);
    const atMax = indicators.length >= MAX_INDICATORS;
    const handleAddType = (type) => {
        const t = INDICATOR_TYPES.find(i => i.value === type);
        setNewType(type);
        setNewPeriod(String(t?.defaultPeriod || 20));
        setNewColor(t?.defaultColor || '#2196f3');
        setAddError(null);
    };
    // Open the inline editor for an indicator, seeding the period field with its current value.
    const startEdit = (ind) => { setEditingId(ind.id); setEditPeriod(String(ind.period)); };
    // Confirm the period edit (✓ button or Enter) and close the editor. X closes without applying, color swatches
    // apply the period together with the picked color — so there's always an explicit way to commit.
    const confirmEdit = (id) => { updateIndicator(id, { period: clampPeriod(editPeriod) }); setEditingId(null); };
    const handleAdd = () => {
        const result = addIndicator(newType, clampPeriod(newPeriod), newColor);
        if (result && result.ok === false) {
            setAddError(result.error);
            return;
        }
        setAddError(null);
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
                            className="group rounded-lg bg-surface/50 hover:bg-surface transition-colors duration-150 min-w-0"
                        >
                            <div className="flex items-center gap-2 px-2 py-1.5 min-h-[34px] min-w-0">
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
                                {editingId === ind.id ? (
                                    <button
                                        onClick={() => setEditingId(null)}
                                        className="ml-auto shrink-0 p-1 border-none bg-transparent cursor-pointer text-fg-muted hover:text-fg rounded hover:bg-surface transition-colors"
                                        title={t('chart.indicators.cancel')}
                                    >
                                        <X className="w-3.5 h-3.5" />
                                    </button>
                                ) : (
                                    <>
                                        <span className="text-[11px] text-fg-muted font-mono shrink-0">{ind.period}</span>
                                        <div className="flex items-center gap-0.5 ml-auto shrink-0 opacity-60 group-hover:opacity-100 transition-opacity">
                                            <button
                                                onClick={() => startEdit(ind)}
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
                            {/* Edit controls live on their OWN wrapping row so the period field + color swatches never
                                widen the indicator row and spill out of the narrow sidebar. */}
                            {editingId === ind.id && (
                                <div className="flex items-center flex-wrap gap-1.5 px-2 pb-2 pl-7">
                                    <input
                                        type="text"
                                        inputMode="numeric"
                                        autoFocus
                                        value={editPeriod}
                                        onChange={e => setEditPeriod(digitsOnly(e.target.value))}
                                        onKeyDown={e => { if (e.key === 'Enter') confirmEdit(ind.id); }}
                                        className="w-12 px-1.5 py-0.5 rounded bg-bg-base border border-border-default text-xs text-fg outline-none focus:border-accent"
                                    />
                                    <button
                                        onClick={() => confirmEdit(ind.id)}
                                        className="shrink-0 inline-flex items-center justify-center w-6 h-6 rounded-md bg-accent text-white border-none cursor-pointer hover:bg-accent-bright transition-colors"
                                        title={t('chart.indicators.confirm')}
                                    >
                                        <Check className="w-3.5 h-3.5" />
                                    </button>
                                    {/* Color swatches apply the new period together with the picked color (one-tap shortcut). */}
                                    <div className="flex flex-wrap items-center gap-1">
                                        {COLOR_PRESETS.map(c => (
                                            <button
                                                key={c}
                                                onClick={() => { updateIndicator(ind.id, { period: clampPeriod(editPeriod), color: c }); setEditingId(null); }}
                                                className="w-4 h-4 rounded-full border-none cursor-pointer hover:scale-125 transition-transform"
                                                style={{
                                                    background: c,
                                                    outline: ind.color === c ? '2px solid var(--color-accent)' : 'none',
                                                    outlineOffset: '1px',
                                                }}
                                            />
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
            {}
            {showAddForm ? (
                <div className="p-2 rounded-lg bg-surface/50 border border-border-default space-y-2">
                    <div className="flex flex-wrap gap-1">
                        {availableTypes.map(t => (
                            <button
                                key={t.value}
                                onClick={() => handleAddType(t.value)}
                                className={`flex-1 min-w-[40px] px-1.5 py-1 min-h-[28px] rounded-md text-[11px] font-semibold border transition-all duration-150 cursor-pointer ${newType === t.value
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
                            type="text"
                            inputMode="numeric"
                            value={newPeriod}
                            onChange={e => setNewPeriod(digitsOnly(e.target.value))}
                            onBlur={() => setNewPeriod(p => String(clampPeriod(p)))}
                            className="w-16 px-2 py-1 rounded-md bg-bg-base border border-border-default text-xs text-fg outline-none focus:border-accent"
                        />
                    </div>
                    <div className="flex items-center flex-wrap gap-1.5">
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
                    {addError && (
                        <div className="flex items-start gap-1.5 text-[11px] text-danger">
                            <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" />
                            <span>{t(`chart.indicators.error.${addError}`, { max: MAX_INDICATORS })}</span>
                        </div>
                    )}
                    <div className="flex gap-1.5">
                        <button
                            onClick={handleAdd}
                            className="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 min-h-[32px] rounded-md bg-accent text-white text-xs font-semibold border-none cursor-pointer hover:bg-accent-bright transition-colors"
                        >
                            <Plus className="w-3 h-3" /> {t('chart.indicators.add')}
                        </button>
                        <button
                            onClick={() => setShowAddForm(false)}
                            className="px-3 py-1.5 min-h-[32px] rounded-md bg-transparent text-fg-muted text-xs font-medium border border-border-default cursor-pointer hover:text-fg hover:border-fg-subtle transition-colors"
                        >
                            {t('chart.indicators.cancel')}
                        </button>
                    </div>
                </div>
            ) : atMax ? (
                <div className="flex items-center justify-center gap-1.5 px-3 py-1.5 min-h-[32px] rounded-lg text-[11px] font-medium text-fg-subtle bg-surface/30 border border-dashed border-border-default">
                    <AlertCircle className="w-3 h-3 shrink-0" />
                    {t('chart.indicators.error.max', { max: MAX_INDICATORS })}
                </div>
            ) : (
                <button
                    onClick={() => { setAddError(null); setShowAddForm(true); }}
                    className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 min-h-[32px] rounded-lg text-xs font-medium text-fg-muted bg-transparent border border-dashed border-border-default cursor-pointer hover:text-fg hover:border-fg-subtle hover:bg-surface/50 transition-all duration-150"
                >
                    <Plus className="w-3 h-3" /> {t('chart.indicators.addIndicator')}
                </button>
            )}
        </div>
    );
};
export default IndicatorPanel;
