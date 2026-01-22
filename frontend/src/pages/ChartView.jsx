import { useState, useEffect } from 'react';
import { useSearchParams, useParams } from 'react-router-dom';
import HistoricalChart from '../components/HistoricalChart';
import { getCryptoHistory } from '../services/marketService';
import { getCoinIds } from '../constants/coins';
import './ChartView.css';

const ChartView = () => {
  const { coinId } = useParams(); // Get coinId from URL params like /chart/bitcoin
  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [chartData, setChartData] = useState(null);
  const [filteredData, setFilteredData] = useState(null);
  const [hoveredCandle, setHoveredCandle] = useState(null); // Track hovered candle
  
  const [assetType, setAssetType] = useState('CRYPTO');
  const [symbol, setSymbol] = useState(coinId || searchParams.get('symbol') || 'bitcoin');
  const [timeRange, setTimeRange] = useState(searchParams.get('range') || '1Y');
  const [customSymbol, setCustomSymbol] = useState('');

  // Frontend sayfalarında görünen EXACT AYNI varlık listeleri
  const presetSymbols = {
    // BIST sayfasında görünen 20 hisse
    BIST: [
      'AKBNK', 'ASELS', 'ASTOR', 'BIMAS', 'EREGL',
      'GARAN', 'GMSTR', 'HALKS', 'ISCTR', 'ISIST',
      'KCHOL', 'PGSUS', 'SAHOL', 'SASA', 'TCELL',
      'TERA', 'THYAO', 'TRALT', 'TUPRS', 'YKBNK'
    ],
    // US Stocks sayfasında görünen 20 hisse
    US: [
      'AAPL', 'AMD', 'AMZN', 'BAC', 'DIS',
      'GOOGL', 'GS', 'INTC', 'JNJ', 'JPM',
      'KO', 'MA', 'META', 'MSFT', 'NFLX',
      'NVDA', 'TSLA', 'V', 'WMT', 'XOM'
    ],
    // Crypto sayfasında görünen cryptolar (API ID'leri olarak) - Tüm 12 coin
    CRYPTO: getCoinIds(),
    // Metals sayfasında görünen metaller
    METAL: [
      'PAXG', 'XAUT', 'KAG'
    ]
  };

  // Date filtering function
  const filterDataByTimeRange = (candles, range) => {
    if (!candles || candles.length === 0) return candles;
    
    const now = new Date();
    let cutoffDate;
    
    switch (range) {
      case '1M':
        cutoffDate = new Date(now.getTime() - (30 * 24 * 60 * 60 * 1000));
        break;
      case '3M':
        cutoffDate = new Date(now.getTime() - (90 * 24 * 60 * 60 * 1000));
        break;
      case '1Y':
      default:
        return candles; // Show all data for 1Y
    }
    
    return candles.filter(candle => {
      const candleDate = new Date(candle.date);
      return candleDate >= cutoffDate;
    });
  };

  // Initialize from URL params
  useEffect(() => {
    // If we have a coinId from URL params, use it
    if (coinId) {
      setSymbol(coinId);
      setAssetType('CRYPTO');
    }
    
    const urlType = searchParams.get('type');
    const urlSymbol = searchParams.get('symbol');
    const urlRange = searchParams.get('range');
    
    if (urlType && ['BIST', 'US', 'CRYPTO', 'METAL'].includes(urlType)) {
      setAssetType(urlType);
    }
    if (urlSymbol && !coinId) {
      setSymbol(urlSymbol);
    }
    if (urlRange && ['1M', '3M', '1Y'].includes(urlRange)) {
      setTimeRange(urlRange);
    }
  }, [coinId]);

  useEffect(() => {
    // Reset to first preset symbol when asset type changes (unless URL has symbol)
    if (!searchParams.get('symbol')) {
      setSymbol(presetSymbols[assetType][0]);
    }
    setCustomSymbol('');
  }, [assetType]);

  useEffect(() => {
    if (symbol) {
      // Update URL params
      setSearchParams({ type: assetType, symbol, range: timeRange });
      fetchData();
    }
  }, [symbol, assetType, timeRange]);

  // Update filtered data when chartData or timeRange changes
  useEffect(() => {
    if (chartData && chartData.candles) {
      const filtered = filterDataByTimeRange(chartData.candles, timeRange);
      setFilteredData({
        ...chartData,
        candles: filtered
      });
      // Initialize SMA calculation by setting the first candle as hovered
      if (filtered.length > 0 && !hoveredCandle) {
        setHoveredCandle(filtered[filtered.length - 1]);
      }
    }
  }, [chartData, timeRange]);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    
    try {
      console.log('Fetching chart data for:', { symbol, assetType });
      
      // Only fetch for CRYPTO type currently
      if (assetType !== 'CRYPTO') {
        setError(`${assetType} historical data not yet implemented`);
        setChartData(null);
        return;
      }
      
      const data = await getCryptoHistory(symbol);
      console.log('Received candle data:', data);
      
      // Transform backend data format to chart format
      const transformedCandles = data.map(candle => ({
        date: candle.candleDate,
        price: candle.close,
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close
      }));
      
      // Create chart data structure that HistoricalChart expects
      setChartData({
        candles: transformedCandles,
        currentTrend: transformedCandles.length > 0 && transformedCandles[transformedCandles.length - 1].close > transformedCandles[0].close ? 'UP' : 'DOWN',
        analysis: {
          sma20: true,
          sma50: true
        }
      });
    } catch (err) {
      console.error('Error fetching historical data:', err);
      setError(err.message || 'Failed to load historical data');
    } finally {
      setLoading(false);
    }
  };

  const handleCustomSymbol = (e) => {
    e.preventDefault();
    if (customSymbol.trim()) {
      setSymbol(customSymbol.trim().toUpperCase());
    }
  };

  const getTrendBadgeClass = (trend) => {
    switch (trend) {
      case 'UP': return 'trend-badge trend-up';
      case 'DOWN': return 'trend-badge trend-down';
      default: return 'trend-badge trend-neutral';
    }
  };

  return (
    <div className="chart-view">
      <div className="chart-header">
        <h1>📈 Historical Analysis</h1>
        <p>Analyze price trends with technical indicators</p>
      </div>

      <div className="chart-controls">
        {/* Asset Type Selector */}
        <div className="control-group">
          <label>Asset Type</label>
          <div className="button-group">
            {['BIST', 'US', 'CRYPTO', 'METAL'].map(type => (
              <button
                key={type}
                className={assetType === type ? 'active' : ''}
                onClick={() => setAssetType(type)}
              >
                {type === 'BIST' ? '🇹🇷 BIST' : 
                 type === 'US' ? '🇺🇸 US' : 
                 type === 'CRYPTO' ? '₿ Crypto' :
                 '🪙 Metal'}
              </button>
            ))}
          </div>
        </div>

        {/* Symbol Selector */}
        <div className="control-group">
          <label>Symbol</label>
          <div className="symbol-selector">
            <select value={symbol} onChange={(e) => setSymbol(e.target.value)}>
              {presetSymbols[assetType].map(sym => (
                <option key={sym} value={sym}>{sym}</option>
              ))}
            </select>
            <form onSubmit={handleCustomSymbol} className="custom-symbol">
              <input
                type="text"
                placeholder="Custom symbol..."
                value={customSymbol}
                onChange={(e) => setCustomSymbol(e.target.value)}
              />
              <button type="submit">Go</button>
            </form>
          </div>
        </div>
      </div>

      {/* Analysis Summary */}
      {filteredData && !loading && (
        <div className="analysis-summary">
          <h2 className="crypto-name-prominent">{symbol.toUpperCase()} - Live Chart Data</h2>
          {(() => {
            // Use hovered candle or latest candle
            const displayCandle = hoveredCandle || (filteredData.candles.length > 0 ? filteredData.candles[filteredData.candles.length - 1] : null);
            const candles = filteredData.candles;
            
            if (!displayCandle) return null;
            
            // Calculate SMAs for current position
            const candleIndex = hoveredCandle ? candles.indexOf(hoveredCandle) : candles.length - 1;
            const calculateSMA = (period) => {
              if (candleIndex < period - 1) return null;
              const sum = candles.slice(candleIndex - period + 1, candleIndex + 1).reduce((acc, c) => acc + c.close, 0);
              return sum / period;
            };
            
            const sma20 = calculateSMA(20);
            const sma50 = calculateSMA(50);
            const sma200 = calculateSMA(200);
            
            return (
              <div className="summary-grid">
                <div className="summary-item">
                  <span className="summary-label">Date</span>
                  <span className="summary-value">
                    {new Date(displayCandle.date).toLocaleDateString('tr-TR')}
                  </span>
                </div>
                <div className="summary-item">
                  <span className="summary-label">Open</span>
                  <span className="summary-value">
                    {new Intl.NumberFormat('tr-TR', {
                      style: 'currency',
                      currency: 'USD'
                    }).format(displayCandle.open)}
                  </span>
                </div>
                <div className="summary-item">
                  <span className="summary-label">High</span>
                  <span className="summary-value">
                    {new Intl.NumberFormat('tr-TR', {
                      style: 'currency',
                      currency: 'USD'
                    }).format(displayCandle.high)}
                  </span>
                </div>
                <div className="summary-item">
                  <span className="summary-label">Low</span>
                  <span className="summary-value">
                    {new Intl.NumberFormat('tr-TR', {
                      style: 'currency',
                      currency: 'USD'
                    }).format(displayCandle.low)}
                  </span>
                </div>
                <div className="summary-item">
                  <span className="summary-label">Close</span>
                  <span className="summary-value">
                    {new Intl.NumberFormat('tr-TR', {
                      style: 'currency',
                      currency: 'USD'
                    }).format(displayCandle.close)}
                  </span>
                </div>
                {sma20 && (
                  <div className="summary-item">
                    <span className="summary-label">SMA 20</span>
                    <span className="summary-value">
                      {new Intl.NumberFormat('tr-TR', {
                        style: 'currency',
                        currency: 'USD'
                      }).format(sma20)}
                    </span>
                  </div>
                )}
                {sma50 && (
                  <div className="summary-item">
                    <span className="summary-label">SMA 50</span>
                    <span className="summary-value">
                      {new Intl.NumberFormat('tr-TR', {
                        style: 'currency',
                        currency: 'USD'
                      }).format(sma50)}
                    </span>
                  </div>
                )}
                {sma200 && (
                  <div className="summary-item">
                    <span className="summary-label">SMA 200</span>
                    <span className="summary-value">
                      {new Intl.NumberFormat('tr-TR', {
                        style: 'currency',
                        currency: 'USD'
                      }).format(sma200)}
                    </span>
                  </div>
                )}
              </div>
            );
          })()}
        </div>
      )}

      {/* Chart Area with Time Range */}
      <div className="chart-with-controls">
        {/* Time Range Vertical Buttons */}
        <div className="time-range-vertical">
          <label className="range-label">Time</label>
          {['1Y', '3M', '1M'].map(range => (
            <button
              key={range}
              className={`range-btn ${timeRange === range ? 'active' : ''}`}
              onClick={() => setTimeRange(range)}
            >
              {range}
            </button>
          ))}
        </div>

        <div className="chart-container">
        {loading && (
          <div className="chart-loading">
            <div className="spinner"></div>
            <p>Loading historical data...</p>
          </div>
        )}
        
        {error && (
          <div className="chart-error">
            <p>❌ {error}</p>
            <button onClick={fetchData}>Retry</button>
          </div>
        )}
        
        {!loading && !error && filteredData && (
          <HistoricalChart 
            data={filteredData} 
            symbol={symbol}
            onHoverCandle={setHoveredCandle}
          />
        )}
        </div>
      </div>

      {/* Info Section */}
      <div className="chart-info">
        <h3>About Technical Indicators</h3>
        <div className="info-grid">
          <div className="info-card">
            <h4>SMA 20 (Simple Moving Average)</h4>
            <p>20-day average price. Short-term trend indicator. Price above SMA20 suggests uptrend.</p>
          </div>
          <div className="info-card">
            <h4>SMA 50 (Simple Moving Average)</h4>
            <p>50-day average price. Medium-term trend indicator. Golden cross (SMA20 &gt; SMA50) is bullish.</p>
          </div>
          <div className="info-card">
            <h4>Trend Analysis</h4>
            <p>Uptrend: Price &gt; SMA20 | Downtrend: Price &lt; SMA20 | Neutral: Close to SMA20</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ChartView;
