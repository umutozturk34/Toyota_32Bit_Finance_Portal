const ACCENT = '#6366f1';

export function chartPalette(isDark) {
  return {
    accent: ACCENT,
    muted: isDark ? '#6b6b7a' : '#94a3b8',
    grid: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)',
    border: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
    tooltipBg: isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)',
    tooltipFg: isDark ? '#e2e2ea' : '#1a1a2e',
    fg: isDark ? '#e8e8f0' : '#0f172a',
  };
}

export function timeAxis(palette, overrides = {}) {
  return {
    type: 'time',
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: palette.muted, fontSize: 10, hideOverlap: true },
    splitLine: { show: false },
    ...overrides,
  };
}

export function valueAxis(palette, overrides = {}) {
  return {
    type: 'value',
    scale: true,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: palette.muted, fontSize: 10 },
    splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } },
    ...overrides,
  };
}

export function dataZoomBlock(palette, overrides = {}) {
  return [
    {
      type: 'inside',
      filterMode: 'filter',
      zoomOnMouseWheel: true,
      moveOnMouseMove: true,
      moveOnMouseWheel: false,
      preventDefaultMouseMove: true,
    },
    {
      type: 'slider',
      height: 18,
      bottom: 8,
      filterMode: 'filter',
      borderColor: 'transparent',
      backgroundColor: 'transparent',
      dataBackground: {
        lineStyle: { color: `${ACCENT}60`, width: 1 },
        areaStyle: { color: `${ACCENT}20` },
      },
      selectedDataBackground: {
        lineStyle: { color: ACCENT, width: 1 },
        areaStyle: { color: `${ACCENT}40` },
      },
      fillerColor: 'rgba(99,102,241,0.12)',
      handleStyle: { color: ACCENT, borderColor: ACCENT },
      moveHandleStyle: { color: ACCENT, opacity: 0.4 },
      showDetail: false,
      brushSelect: false,
      textStyle: { color: palette.muted, fontSize: 9 },
      ...overrides,
    },
  ];
}

export function tooltipBase(palette, overrides = {}) {
  return {
    trigger: 'axis',
    backgroundColor: palette.tooltipBg,
    borderWidth: 0,
    textStyle: { color: palette.tooltipFg, fontSize: 11 },
    axisPointer: { type: 'cross', label: { backgroundColor: palette.muted } },
    ...overrides,
  };
}

export function lineSeriesDefaults(color, dataLength) {
  return {
    type: 'line',
    smooth: dataLength < 200,
    showSymbol: false,
    sampling: 'lttb',
    itemStyle: { color },
    lineStyle: { width: 2, color },
  };
}

export function areaGradient(color, topOpacity = 0.33) {
  const alphaHex = Math.round(topOpacity * 255).toString(16).padStart(2, '0');
  return {
    type: 'linear',
    x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
      { offset: 0, color: `${color}${alphaHex}` },
      { offset: 1, color: `${color}00` },
    ],
  };
}

export function legendBase(palette, overrides = {}) {
  return {
    type: 'scroll',
    top: 6,
    textStyle: { color: palette.muted, fontSize: 11, fontFamily: 'ui-monospace,monospace' },
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
    ...overrides,
  };
}

export const CHART_ACCENT = ACCENT;
