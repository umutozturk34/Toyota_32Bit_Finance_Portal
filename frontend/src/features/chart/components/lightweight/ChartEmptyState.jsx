import React from 'react';
import { useTranslation } from 'react-i18next';
import { LineChart } from 'lucide-react';
import Card from '../../../../shared/components/card';

// Placeholder shown while the chart has no candles yet. Purely presentational — owns no chart refs.
const ChartEmptyState = () => {
    const { t } = useTranslation();
    return (
        <Card variant="elevated" radius="xl" padding="lg" backdropBlur interactive={false} className="flex flex-col items-center justify-center h-80">
            <LineChart className="w-12 h-12 mb-3 text-fg-subtle" />
            <p className="text-fg-muted text-sm">{t('chart.waitingForData')}</p>
        </Card>
    );
};

export default ChartEmptyState;
