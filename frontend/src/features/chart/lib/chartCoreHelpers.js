export const COMPARE_COLORS = ['#ef4444', '#10b981', '#f59e0b', '#06b6d4'];

export const toEpochSec = (chartTime) => {
    if (chartTime == null) return null;
    if (typeof chartTime === 'number') return Math.floor(chartTime);
    if (typeof chartTime === 'object' && chartTime.year) {
        return Math.floor(Date.UTC(chartTime.year, chartTime.month - 1, chartTime.day) / 1000);
    }
    return null;
};

export const dimColor = (color, alpha = 0.4) => {
    if (!color) return color;
    const hex = color.replace('#', '');
    const r = parseInt(hex.substring(0, 2), 16);
    const g = parseInt(hex.substring(2, 4), 16);
    const b = parseInt(hex.substring(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

export const toChartTime = (dateStr) => {
    if (!dateStr) return 0;
    const s = String(dateStr);
    if (s.length >= 10) {
        const [y, m, d] = s.substring(0, 10).split('-').map(Number);
        return { year: y, month: m, day: d };
    }
    return new Date(s).getTime() / 1000;
};

export const chartTimeKey = (t) => {
    if (!t) return '';
    if (typeof t === 'object') return `${t.year}-${t.month}-${t.day}`;
    return String(t);
};

export const chartTimeEqual = (a, b) => {
    if (a === b) return true;
    if (a && b && typeof a === 'object' && typeof b === 'object') {
        return a.year === b.year && a.month === b.month && a.day === b.day;
    }
    return false;
};

export const analyzeTrend = (d) => {
    if (!d || d.length < 20) return null;
    const recent = d.slice(-20);
    const first = recent.slice(0, 10);
    const second = recent.slice(10);
    const avg1 = first.reduce((s, c) => s + c.close, 0) / first.length;
    const avg2 = second.reduce((s, c) => s + c.close, 0) / second.length;
    const pct = ((avg2 - avg1) / avg1) * 100;
    if (pct > 2) return { direction: 'up', change: pct };
    if (pct > 0.5) return { direction: 'up', change: pct };
    if (pct < -2) return { direction: 'down', change: pct };
    if (pct < -0.5) return { direction: 'down', change: pct };
    return { direction: 'neutral', change: pct };
};
