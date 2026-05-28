import { CrosshairMode, LineStyle } from 'lightweight-charts';

export const getChartOptions = (isDark) => ({
    autoSize: true,
    layout: {
        background: { color: isDark ? '#050506' : '#ffffff' },
        textColor: isDark ? '#7a7a85' : '#6b7280',
    },
    grid: {
        vertLines: { color: isDark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(0, 0, 0, 0.06)' },
        horzLines: { color: isDark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(0, 0, 0, 0.06)' },
    },
    crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: 'rgba(94, 106, 210, 0.5)', width: 1, style: LineStyle.Dashed },
        horzLine: { color: 'rgba(94, 106, 210, 0.5)', width: 1, style: LineStyle.Dashed },
    },
    rightPriceScale: { borderColor: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)' },
    timeScale: { borderColor: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)', timeVisible: true, secondsVisible: false, minBarSpacing: 0.07 },
    handleScale: { axisPressedMouseMove: true },
    handleScroll: { vertTouchDrag: true },
});
