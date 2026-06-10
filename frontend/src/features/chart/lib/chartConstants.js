import { Activity, PenTool } from 'lucide-react';

// Sidebar tabs + selectable chart time ranges. Pure config shared by the chart UI; VIOP drops 5Y/ALL because
// derivatives contracts don't have that much history.
export const TABS = [
    { id: 'indicators', labelKey: 'chart.tabs.indicators', Icon: Activity },
    { id: 'drawings', labelKey: 'chart.tabs.drawings', Icon: PenTool },
];

export const TIME_RANGES_FULL = [
    { id: '1W', labelKey: 'chart.range.1W', months: 0 },
    { id: '1M', labelKey: 'chart.range.1M', months: 1 },
    { id: '3M', labelKey: 'chart.range.3M', months: 3 },
    { id: '6M', labelKey: 'chart.range.6M', months: 6 },
    { id: '1Y', labelKey: 'chart.range.1Y', months: 12 },
    { id: '3Y', labelKey: 'chart.range.3Y', months: 36 },
    { id: '5Y', labelKey: 'chart.range.5Y', months: 60 },
    { id: 'ALL', labelKey: 'chart.range.ALL', months: 0 },
];

export const TIME_RANGES_VIOP = TIME_RANGES_FULL.filter(({ id }) => id !== '5Y' && id !== 'ALL');
