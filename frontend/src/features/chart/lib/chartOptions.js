import { CrosshairMode, LineStyle } from 'lightweight-charts';

export const getChartOptions = (isDark) => ({
    // No autoSize: lightweight-charts' autoSize would OWN sizing and ignore applyOptions(width/height), which
    // (a) made our manual handleResize a no-op — the chart stayed stuck at the fullscreen height after exit —
    // and (b) re-enabled itself on every theme re-apply (applyOptions(getChartOptions)). Both the main chart
    // and the sub-charts size manually via their own ResizeObserver/handleResize, so the lightweight-charts
    // default (autoSize off) is correct. (createChart also passes autoSize:false explicitly for clarity.)
    layout: {
        attributionLogo: false,
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
