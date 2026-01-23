import React, { useRef, useCallback } from 'react';
import ReactApexChart from 'react-apexcharts';

const HistoricalChart = ({ data, symbol, onHoverCandle }) => {
  const isZoomingRef = useRef(false);
  const isPanningRef = useRef(false);
  const hoverTimeoutRef = useRef(null);

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

  const colors = {
    candleUp: '#26a69a',
    candleDown: '#ef5350',
    sma20: '#3498db',
    sma50: '#f39c12',
    sma200: '#9b59b6'
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

  const handleHover = useCallback((candle) => {
    if (isZoomingRef.current || isPanningRef.current || !onHoverCandle) return;
    
    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current);
    }
    
    hoverTimeoutRef.current = setTimeout(() => {
      onHoverCandle(candle);
    }, 50);
  }, [onHoverCandle]);

  const options = {
    chart: {
      type: 'candlestick',
      height: 450,
      background: 'transparent',
      toolbar: { 
        show: true,
        tools: {
          download: false,
          selection: false,
          zoom: true,
          zoomin: true,
          zoomout: true,
          pan: true,
          reset: true
        },
        autoSelected: 'pan'
      },
      animations: { enabled: false },
      zoom: {
        enabled: true,
        type: 'x',
        autoScaleYaxis: true
      },
      events: {
        beforeZoom: function(chartContext, { xaxis }) {
          isZoomingRef.current = true;
          return { xaxis };
        },
        zoomed: function() {
          setTimeout(() => {
            isZoomingRef.current = false;
          }, 200);
        },
        mouseDown: function() {
          isPanningRef.current = true;
        },
        mouseUp: function() {
          setTimeout(() => {
            isPanningRef.current = false;
          }, 100);
        },
        mouseMove: function(event, chartContext, config) {
          if (isZoomingRef.current || isPanningRef.current) return;
          
          const dataPointIndex = config.dataPointIndex;
          if (dataPointIndex >= 0 && data.candles[dataPointIndex]) {
            handleHover(data.candles[dataPointIndex]);
          }
        },
        mouseLeave: function() {
          if (!isZoomingRef.current && !isPanningRef.current && data.candles.length > 0) {
            handleHover(data.candles[data.candles.length - 1]);
          }
        },
        scroll: function() {
          isZoomingRef.current = true;
          setTimeout(() => {
            isZoomingRef.current = false;
          }, 300);
        }
      }
    },
    colors: [colors.candleUp, colors.sma20, colors.sma50, colors.sma200],
    stroke: {
      width: [1, 2, 2, 2.5],
      dashArray: [0, 0, 0, 0]
    },
    legend: {
      show: true,
      position: 'top',
      horizontalAlign: 'left',
      fontSize: '13px',
      fontWeight: 700,
      fontFamily: 'Inter, sans-serif',
      labels: {
        colors: '#bdc3c7'
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

export default React.memo(HistoricalChart, (prevProps, nextProps) => {
  return prevProps.data === nextProps.data && prevProps.symbol === nextProps.symbol;
});