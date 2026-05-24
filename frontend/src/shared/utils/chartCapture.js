export function captureEcharts(instance) {
  if (!instance || typeof instance.getDataURL !== 'function') return null;
  try {
    return instance.getDataURL({ pixelRatio: 2, backgroundColor: '#ffffff' });
  } catch {
    return null;
  }
}

export function captureLightweightChart(chart) {
  if (!chart || typeof chart.takeScreenshot !== 'function') return null;
  try {
    return chart.takeScreenshot().toDataURL('image/png');
  } catch {
    return null;
  }
}
