export function getApexThemeOptions(isDark) {
  return {
    chart: {
      background: 'transparent',
      fontFamily: "'Nunito Sans', system-ui, sans-serif",
    },
    theme: {
      mode: isDark ? 'dark' : 'light',
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      style: { fontFamily: "'Nunito Sans', sans-serif", fontSize: '12px' },
    },
    grid: {
      borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
      strokeDashArray: 3,
      padding: { left: 8, right: 8 },
    },
    xaxis: {
      labels: {
        style: {
          colors: isDark ? '#8b8b9a' : '#64748B',
          fontSize: '10px',
          fontFamily: "'Nunito Sans', sans-serif",
        },
      },
      axisBorder: { show: true, color: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)' },
      axisTicks: { show: false },
    },
    yaxis: {
      labels: {
        style: {
          colors: isDark ? '#8b8b9a' : '#64748B',
          fontSize: '10px',
          fontFamily: "'Nunito Sans', sans-serif",
        },
      },
    },
  };
}
