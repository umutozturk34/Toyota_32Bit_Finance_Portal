import React from 'react';
import ReactApexChart from 'react-apexcharts';

const HistoricalChart = ({ data, symbol, onHoverCandle }) => {
  if (!data || !data.candles || data.candles.length === 0) {
    return <div className="chart-empty">No data available</div>;
  }

  const candlestickData = data.candles.map(candle => ({
    x: new Date(candle.candleDate || candle.date),
    y: [candle.open, candle.high, candle.low, candle.close]
  }));

  const calculateSMA = (candles, period) => {
    return candles.map((_, idx) => {
      if (idx < period - 1) return { x: new Date(candles[idx].candleDate || candles[idx].date), y: null };
      const sum = candles.slice(idx - period + 1, idx + 1).reduce((acc, c) => acc + c.close, 0);
      return {
        x: new Date(candles[idx].candleDate || candles[idx].date),
        y: (sum / period).toFixed(2)
      };
    });
  };

  // --- YENİ PROFESYONEL RENK PALETİ ---
  const colors = {
    candleUp: '#26a69a',    // Zümrüt Yeşili
    candleDown: '#ef5350',  // Gül Kırmızısı
    sma20: '#3498db',       // Okyanus Mavisi (Kısa Vade)
    sma50: '#f39c12',       // Günbatımı Turuncusu (Orta Vade)
    sma200: '#9b59b6'       // Kraliyet Moru (Uzun Vade)
  };

  const series = [
    {
      name: 'Fiyat (Candle)',
      type: 'candlestick',
      data: candlestickData
    },
    {
      name: 'SMA 20',
      type: 'line',
      data: calculateSMA(data.candles, 20)
    },
    {
      name: 'SMA 50',
      type: 'line',
      data: calculateSMA(data.candles, 50)
    },
    {
      name: 'SMA 200',
      type: 'line',
      data: calculateSMA(data.candles, 200)
    }
  ];

  const options = {
    chart: {
      type: 'candlestick',
      height: 450,
      background: 'transparent',
      toolbar: { show: false },
      animations: { enabled: false },
      events: {
        mouseMove: function(event, chartContext, config) {
          const dataPointIndex = config.dataPointIndex;
          if (onHoverCandle && dataPointIndex >= 0 && data.candles[dataPointIndex]) {
            onHoverCandle(data.candles[dataPointIndex]);
          }
        },
        mouseLeave: function() {
          if (onHoverCandle && data.candles.length > 0) {
            onHoverCandle(data.candles[data.candles.length - 1]);
          }
        }
      }
    },
    colors: [colors.candleUp, colors.sma20, colors.sma50, colors.sma200],
    stroke: {
      width: [1, 2, 2, 2.5], // SMA 200 biraz daha kalın ve baskın
      dashArray: [0, 0, 0, 0] // Hepsini düz çizgi yaptık, daha ciddi durur
    },
    legend: {
      show: true,
      position: 'top',
      horizontalAlign: 'left',
      fontSize: '13px',
      fontWeight: 700,
      fontFamily: 'Inter, sans-serif',
      labels: {
        colors: '#bdc3c7' // Soft gri yazı rengi
      },
      markers: {
        width: 10,
        height: 10,
        radius: 2,
        offsetX: -4
      },
      itemMargin: {
        horizontal: 15,
        vertical: 10
      }
    },
    xaxis: {
      type: 'datetime',
      labels: { 
        style: { colors: '#7f8c8d', fontSize: '11px' },
        datetimeUTC: false
      },
      axisBorder: { show: false },
      axisTicks: { show: false }
    },
    yaxis: {
      opposite: true,
      labels: {
        style: { colors: '#7f8c8d', fontSize: '11px' },
        formatter: (val) => val ? `$${val.toLocaleString()}` : ''
      }
    },
    plotOptions: {
      candlestick: {
        colors: {
          upward: colors.candleUp,
          downward: colors.candleDown
        }
      }
    },
    grid: {
      borderColor: 'rgba(255, 255, 255, 0.05)',
      strokeDashArray: 2
    }
  };

  return (
    <div className="historical-chart">
      <ReactApexChart 
        options={options} 
        series={series} 
        type="candlestick" 
        height={450} 
      />
    </div>
  );
};

export default HistoricalChart;