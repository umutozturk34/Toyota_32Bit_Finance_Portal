import React from 'react';
import { useTranslation } from 'react-i18next';

const ChartTextEditInput = ({ textEditState, isDark, textDoneRef, commitTextEdit, cancelTextEdit }) => {
    const { t } = useTranslation();
    if (!textEditState) return null;
    return (
        <input
            autoFocus
            type="text"
            maxLength={120}
            placeholder={t('chart.textInputPlaceholder')}
            className="absolute outline-none"
            style={{
                left: textEditState.x,
                top: textEditState.y - 18,
                fontSize: 14,
                fontFamily: 'Inter, sans-serif',
                fontWeight: 500,
                color: isDark ? '#EDEDEF' : '#0f172a',
                background: isDark ? 'rgba(10,10,14,0.95)' : '#ffffff',
                border: '1.5px solid var(--color-accent)',
                borderRadius: 6,
                padding: '4px 8px',
                zIndex: 20,
                minWidth: 80,
                maxWidth: 320,
                caretColor: 'var(--color-accent)',
                boxShadow: '0 2px 8px rgba(94,106,210,0.18)',
                letterSpacing: '0.01em',
            }}
            onChange={(e) => {
                const el = e.target;
                el.style.width = '0';
                el.style.width = `${Math.max(80, Math.min(320, el.scrollWidth + 16))}px`;
            }}
            onKeyDown={(e) => {
                e.stopPropagation();
                if (e.key === 'Enter') {
                    e.preventDefault();
                    textDoneRef.current = true;
                    commitTextEdit(e.target.value);
                } else if (e.key === 'Escape') {
                    textDoneRef.current = true;
                    cancelTextEdit();
                }
            }}
            onBlur={(e) => {
                if (textDoneRef.current) return;
                textDoneRef.current = true;
                if (e.target.value.trim()) commitTextEdit(e.target.value);
                else cancelTextEdit();
            }}
        />
    );
};

export default ChartTextEditInput;
