import { ColorType, CrosshairMode, LineStyle } from 'lightweight-charts';

export const getChartOptions = (isDark) => ({
    // No autoSize: lightweight-charts' autoSize would OWN sizing and ignore applyOptions(width/height), which
    // (a) made our manual handleResize a no-op — the chart stayed stuck at the fullscreen height after exit —
    // and (b) re-enabled itself on every theme re-apply (applyOptions(getChartOptions)). Both the main chart
    // and the sub-charts size manually via their own ResizeObserver/handleResize, so the lightweight-charts
    // default (autoSize off) is correct. (createChart also passes autoSize:false explicitly for clarity.)
    layout: {
        attributionLogo: false,
        // Flat, crisp plot background — no vertical gradient (the gradient darkened the lower half and read as a
        // muddy "shadow"). Dark is a clean near-black (NOT pure #000, so candles/grid keep depth); light is a
        // clean off-white, both a touch off the card so the plot still reads as its own surface.
        background: isDark
            ? { type: ColorType.Solid, color: '#0e0e14' }
            : { type: ColorType.Solid, color: '#f4f7fb' },
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
