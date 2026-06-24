import React from 'react';
import { useTranslation } from 'react-i18next';
import DatePickerPopover from '../../../../shared/components/form/DatePickerPopover';

// Toolbar date picker + time-range buttons row. The calendar icon opens a native date picker bounded to the
// loaded data range; choosing a date pins that day (LENS + analytics panel + legend + crosshair jump to it).
// Purely presentational — it owns no chart refs and only fires the callbacks handed down.
const ChartTimeRangeBar = ({ pickedDateValue, dataDateBounds, selectDate, timeRanges, timeRange, onTimeRangeChange }) => {
    const { t } = useTranslation();
    return (
        <div className="flex items-center gap-1 px-2 sm:px-3 py-1.5 border-b border-border-default bg-surface/40">
            <div className="shrink-0 mr-1">
                <DatePickerPopover
                    compact
                    value={pickedDateValue}
                    minDate={dataDateBounds.min}
                    maxDate={dataDateBounds.max}
                    onChange={selectDate}
                />
            </div>
            <div className="flex items-center gap-1 flex-nowrap overflow-x-auto scrollbar-thin min-w-0">
                {timeRanges.map(({ id, labelKey }) => {
                    const isActive = timeRange === id;
                    return (
                        <button
                            key={id}
                            onClick={() => onTimeRangeChange?.(id)}
                            className={`shrink-0 min-h-[32px] px-2 sm:px-2.5 py-1 rounded-md text-[10px] sm:text-[11px] font-semibold tracking-wide border-none cursor-pointer transition-all duration-200 ${isActive ? 'bg-indigo-400/15 text-indigo-400 shadow-[0_0_12px_rgba(99,102,241,0.18)]' : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface'}`}
                        >
                            {t(labelKey)}
                        </button>
                    );
                })}
            </div>
        </div>
    );
};

export default ChartTimeRangeBar;
