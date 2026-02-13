import React, { useRef, useCallback, useState } from 'react';
import ReactApexChart from 'react-apexcharts';

const HistoricalChart = ({ data, symbol, onHoverCandle, assetType = 'CRYPTO' }) => {
  const isZoomingRef = useRef(false);
  const isPanningRef = useRef(false);
  const hoverTimeoutRef = useRef(null);
  
  // Chart type toggle: 'candlestick' or 'line'
  const [chartType, setChartType] = useState('candlestick');
  // Volume indicator toggle
  const [showVolume, setShowVolume] = useState(false);

  if (!data || !data.candles || data.candles.length === 0) {
    return <div className="chart-empty">No data available</div>;
  }

  // Determine currency based on asset type
  const currency = (assetType === 'BIST' || assetType === 'FOREX') ? 'TRY' : 'USD';
  const currencySymbol = (assetType === 'BIST' || assetType === 'FOREX') ? '₺' : '$';

  // Create categories (formatted dates) for x-axis
  const categories = data.candles.map(candle => {
    const date = new Date(candle.candleDate || candle.date);
    return date.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short' });
  });

  const candlestickData = data.candles.map((candle, index) => ({
    x: index,
    y: [candle.open, candle.high, candle.low, candle.close]
  }));

  const lineData = data.candles.map((candle, index) => ({
    x: index,
    y: candle.close
  }));

  const volumeData = data.candles.map((candle, index) => ({
    x: index,
    y: candle.volume && candle.volume > 0 ? candle.volume : 0
  }));

  const calculateSMA = (candles, period) => {
    return candles.map((_, idx) => {
      if (idx < period - 1) return { x: idx, y: null };
      const sum = candles.slice(idx - period + 1, idx + 1).reduce((acc, c) => acc + c.close, 0);
      return {
        x: idx,
        y: (sum / period).toFixed(2)
      };
    });
  };

  const colors = {
    candleUp: '#26a69a',
    candleDown: '#ef5350',
    line: '#3498db',
    sma20: '#3498db',
    sma50: '#f39c12',
    sma200: '#9b59b6',
    volume: '#7f8c8d'
  };

  // Build series based on chart type
  const priceSeries = chartType === 'candlestick' 
    ? [{
        name: 'Fiyat (Mum)',
        type: 'candlestick',
        data: candlestickData
      }]
    : [{
        name: 'Fiyat (Çizgi)',
        type: 'line',
        data: lineData
      }];

  const series = [
    ...priceSeries,
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
      type: chartType,
      height: showVolume ? 350 : 450,
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
    colors: chartType === 'candlestick' 
      ? [colors.candleUp, colors.sma20, colors.sma50, colors.sma200]
      : [colors.line, colors.sma20, colors.sma50, colors.sma200],
    stroke: {
      width: chartType === 'candlestick' ? [1, 2, 2, 2.5] : [2, 2, 2, 2.5],
      dashArray: [0, 0, 0, 0],
      curve: chartType === 'line' ? 'smooth' : 'straight'
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
      type: 'category',
      categories: categories,
      labels: { 
        style: { colors: '#7f8c8d', fontSize: '11px' },
        rotate: -45,
        rotateAlways: false,
        hideOverlappingLabels: true,
        showDuplicates: false,
        trim: false
      },
      tickAmount: 12,
      axisBorder: { show: false },
      axisTicks: { show: false }
    },
    yaxis: {
      opposite: true,
      labels: {
        style: { colors: '#7f8c8d', fontSize: '11px' },
        formatter: (val) => val ? `${currencySymbol}${val.toLocaleString()}` : ''
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
    },
    tooltip: {
      enabled: true,
      shared: false,
      intersect: chartType === 'line' ? false : true,
      custom: function({ series, seriesIndex, dataPointIndex, w }) {
        const candle = data.candles[dataPointIndex];
        if (!candle) return '';
        
        const date = new Date(candle.candleDate || candle.date).toLocaleDateString('tr-TR', {
          year: 'numeric',
          month: 'short',
          day: 'numeric'
        });
        
        return `
          <div style="padding: 10px; background: #1e1e1e; border: 1px solid #333; border-radius: 4px;">
            <div style="color: #bdc3c7; font-size: 11px; margin-bottom: 6px;">${date}</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 11px;">
              <div>
                <span style="color: #7f8c8d;">Açılış:</span>
                <span style="color: #fff; margin-left: 4px;">${currencySymbol}${candle.open?.toLocaleString() || 'N/A'}</span>
              </div>
              <div>
                <span style="color: #7f8c8d;">Yüksek:</span>
                <span style="color: #26a69a; margin-left: 4px;">${currencySymbol}${candle.high?.toLocaleString() || 'N/A'}</span>
              </div>
              <div>
                <span style="color: #7f8c8d;">Düşük:</span>
                <span style="color: #ef5350; margin-left: 4px;">${currencySymbol}${candle.low?.toLocaleString() || 'N/A'}</span>
              </div>
              <div>
                <span style="color: #7f8c8d;">Kapanış:</span>
                <span style="color: #fff; margin-left: 4px;">${currencySymbol}${candle.close?.toLocaleString() || 'N/A'}</span>
              </div>
              ${candle.volume ? `
                <div style="grid-column: 1 / -1; margin-top: 4px; padding-top: 4px; border-top: 1px solid #333;">
                  <span style="color: #7f8c8d;">Hacim:</span>
                  <span style="color: #fff; margin-left: 4px;">${candle.volume.toLocaleString()}</span>
                </div>
              ` : ''}
            </div>
          </div>
        `;
      }
    }
  };

  const volumeOptions = {
    chart: {
      type: 'bar',
      height: 150,
      background: 'transparent',
      toolbar: { show: false },
      animations: { enabled: false },
      zoom: { enabled: false },
      events: {
        mouseMove: function(event, chartContext, config) {
          if (isZoomingRef.current || isPanningRef.current) return;
          
          const dataPointIndex = config.dataPointIndex;
          if (dataPointIndex >= 0 && data.candles[dataPointIndex]) {
            handleHover(data.candles[dataPointIndex]);
          }
        }
      }
    },
    colors: [colors.volume],
    stroke: {
      show: false,
      width: 0,
      colors: undefined
    },
    fill: {
      opacity: 1
    },
    plotOptions: {
      bar: {
        distributed: false,
        columnWidth: '99%',
        borderRadius: 0,
        borderRadiusApplication: 'end',
        colors: {
          ranges: [{
            from: 0,
            to: Number.MAX_VALUE,
            color: colors.volume
          }]
        }
      }
    },
    dataLabels: {
      enabled: false
    },
    legend: {
      show: false
    },
    xaxis: {
      type: 'category',
      categories: categories,
      labels: {
        show: false
      },
      axisBorder: { show: false },
      axisTicks: { show: false }
    },
    yaxis: {
      opposite: true,
      min: 0,
      forceNiceScale: true,
      labels: {
        style: { colors: '#7f8c8d', fontSize: '10px' },
        formatter: (val) => {
          if (!val) return '0';
          if (val >= 1000000000) return (val / 1000000000).toFixed(1) + 'B';
          if (val >= 1000000) return (val / 1000000).toFixed(1) + 'M';
          if (val >= 1000) return (val / 1000).toFixed(1) + 'K';
          return val.toFixed(0);
        }
      }
    },
    grid: {
      borderColor: 'rgba(255, 255, 255, 0.05)',
      strokeDashArray: 2
    },
    tooltip: {
      enabled: true,
      followCursor: false,
      theme: 'dark',
      x: {
        formatter: (val, opts) => {
          const candle = data.candles[opts.dataPointIndex];
          if (!candle) return '';
          const date = new Date(candle.candleDate || candle.date);
          return date.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
        }
      },
      y: {
        formatter: (val) => {
          if (val === null || val === undefined) return 'Veri Yok';
          return val.toLocaleString();
        },
        title: {
          formatter: () => 'Hacim: '
        }
      }
    }
  };

  return (
    <div className="historical-chart">
      <div className="chart-controls">
        <div className="chart-type-toggle">
          <button 
            className={`toggle-btn ${chartType === 'candlestick' ? 'active' : ''}`}
            onClick={() => setChartType('candlestick')}
          >
            Mum Grafik
          </button>
          <button 
            className={`toggle-btn ${chartType === 'line' ? 'active' : ''}`}
            onClick={() => setChartType('line')}
          >
            Çizgi Grafik
          </button>
        </div>
        <div className="indicator-toggle">
          <button 
            className={`toggle-btn ${showVolume ? 'active' : ''}`}
            onClick={() => setShowVolume(!showVolume)}
          >
            {showVolume ? '✓ ' : ''}Hacim
          </button>
        </div>
      </div>
      
      <ReactApexChart 
        options={options} 
        series={series} 
        type={chartType} 
        height={showVolume ? 350 : 450} 
      />
      
      {showVolume && (
        <div className="volume-chart">
          <ReactApexChart 
            options={volumeOptions}
            series={[{ name: 'Hacim', data: volumeData }]}
            type="bar"
            height={150}
          />
        </div>
      )}
    </div>
  );
};

export default React.memo(HistoricalChart, (prevProps, nextProps) => {
  return prevProps.data === nextProps.data && prevProps.symbol === nextProps.symbol;
});