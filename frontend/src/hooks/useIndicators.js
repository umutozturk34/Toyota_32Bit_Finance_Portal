import { useState, useCallback } from 'react';
let nextId = 1;
const genId = () => `ind-${nextId++}`;
const DEFAULT_INDICATORS = [
    { id: genId(), type: 'SMA', period: 20, color: '#c084fc', visible: false },
    { id: genId(), type: 'EMA', period: 50, color: '#fb923c', visible: false },
];
export default function useIndicators() {
    const [indicators, setIndicators] = useState(DEFAULT_INDICATORS);
    const addIndicator = useCallback((type, period, color) => {
        const defaults = {
            SMA: { period: 20, color: '#c084fc' },
            EMA: { period: 50, color: '#fb923c' },
            RSI: { period: 14, color: '#e91e63' },
            MACD: { period: 12, color: '#06b6d4' },
        };
        const d = defaults[type] || {};
        setIndicators(prev => [
            ...prev,
            {
                id: genId(),
                type,
                period: period || d.period,
                color: color || d.color,
                visible: true,
            },
        ]);
    }, []);
    const removeIndicator = useCallback((id) => {
        setIndicators(prev => prev.filter(ind => ind.id !== id));
    }, []);
    const updateIndicator = useCallback((id, updates) => {
        setIndicators(prev =>
            prev.map(ind => (ind.id === id ? { ...ind, ...updates } : ind))
        );
    }, []);
    const toggleIndicator = useCallback((id) => {
        setIndicators(prev =>
            prev.map(ind => (ind.id === id ? { ...ind, visible: !ind.visible } : ind))
        );
    }, []);
    return {
        indicators,
        addIndicator,
        removeIndicator,
        updateIndicator,
        toggleIndicator,
    };
}
