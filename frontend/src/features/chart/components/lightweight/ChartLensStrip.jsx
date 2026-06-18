import React from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronUp, ScanSearch } from 'lucide-react';
import Card from '../../../../shared/components/card';
import DataWindowPanel from '../DataWindowPanel';

// The whole Lens (top summary strip + right analytics panel) is collapsible — CLOSED by default so the chart
// leads, while the always-on top-left legend covers OHLC + indicators. This is the top summary strip half:
// collapsed it's a single open-button, expanded it's the summary DataWindowPanel with a collapse control.
// Purely presentational — it owns no chart refs.
const ChartLensStrip = ({ lensOpen, setLensOpen, candles, crosshairData, assetType }) => {
    const { t } = useTranslation();
    return (
        <div data-tour="chart-lens">
        {lensOpen ? (
            <Card variant="elevated" radius="xl" padding="none" backdropBlur interactive={false} hoverable={false} className="relative z-10 !overflow-hidden">
                <DataWindowPanel candles={candles} hover={crosshairData} assetType={assetType} variant="summary" />
                <button
                    type="button"
                    onClick={() => setLensOpen(false)}
                    title={t('chart.dataWindow.collapse')}
                    className="absolute right-2 top-2 inline-flex h-7 w-7 items-center justify-center rounded-md border-none bg-transparent text-fg-muted hover:text-fg hover:bg-surface transition-colors cursor-pointer"
                >
                    <ChevronUp className="h-4 w-4" />
                </button>
            </Card>
        ) : (
            <button
                type="button"
                onClick={() => setLensOpen(true)}
                className="w-full flex items-center justify-between gap-2 rounded-xl border border-border-default bg-surface/40 backdrop-blur px-4 py-2 cursor-pointer hover:bg-surface/60 transition-colors"
            >
                <span className="inline-flex items-center gap-1.5 text-xs font-display font-bold uppercase tracking-[0.16em] text-fg-muted">
                    <ScanSearch className="h-4 w-4 text-accent" />
                    {t('chart.dataWindow.title')}
                </span>
                <ChevronDown className="h-4 w-4 text-fg-muted" />
            </button>
        )}
        </div>
    );
};

export default ChartLensStrip;
