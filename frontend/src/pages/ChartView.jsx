import { useState, useEffect, useRef, useMemo } from 'react';
import { useSearchParams, useParams, useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LineChart, ArrowLeft, Clock, BarChart2, Loader2,
  AlertTriangle, TrendingUp, RefreshCw, Activity, X
} from 'lucide-react';
import LightweightChart from '../components/LightweightChart';
import { getCryptoHistory, stockService, forexService, fundService } from '../services/marketService';
import { getCoinIds } from '../constants/coins';
import { getBistSymbols, getBistDisplayName, isCompareOnly } from '../constants/stocks';
import { getForexPairs } from '../constants/forex';
const ASSET_ICONS = {
  BIST: <BarChart2 className="w-4 h-4" />,
  US: <TrendingUp className="w-4 h-4" />,
  CRYPTO: <Activity className="w-4 h-4" />,
  FOREX: <RefreshCw className="w-4 h-4" />,
  FUND: <LineChart className="w-4 h-4" />,
  METAL: <LineChart className="w-4 h-4" />,
};
const ASSET_LABELS = {
  BIST: 'BIST',
  US: 'US',
  CRYPTO: 'Crypto',
  FOREX: 'Forex',
  FUND: 'Fon',
  METAL: 'Metal',
};
const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.1 },
  },
};
const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5, ease: [0.16, 1, 0.3, 1] } },
};
const ChartView = () => {
  const { coinId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [chartData, setChartData] = useState(null);
  const [filteredData, setFilteredData] = useState(null);
  const [assetType, setAssetType] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    const urlType = params.get('type');
    if (urlType && ['BIST', 'US', 'CRYPTO', 'FOREX', 'FUND', 'METAL'].includes(urlType)) return urlType;
    return 'CRYPTO';
  });
  const [symbol, setSymbol] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    const urlType = params.get('type');
    let sym = params.get('symbol') || coinId || 'bitcoin';
    if (urlType === 'BIST' && sym.endsWith('.IS')) sym = sym.replace('.IS', '');
    return sym;
  });
  const [timeRange, setTimeRange] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    const r = params.get('range');
    return (r && ['1M', '3M', '1Y', '5Y'].includes(r)) ? r : '1Y';
  });
  const [customSymbol, setCustomSymbol] = useState('');
  const [retryKey, setRetryKey] = useState(0);
  const [fundList, setFundList] = useState([]);
  const [compareSymbol, setCompareSymbol] = useState(null);
  const [compareData, setCompareData] = useState(null);

  useEffect(() => {
    if (assetType === 'FUND' && fundList.length === 0) {
      fundService.getAllFunds()
        .then(data => setFundList(data || []))
        .catch(() => setFundList([]));
    }
  }, [assetType]);

  useEffect(() => {
    setCompareSymbol(null);
    setCompareData(null);
  }, [assetType, symbol]);

  useEffect(() => {
    if (!compareSymbol) { setCompareData(null); return; }
    let cancelled = false;
    const fetchCompare = async () => {
      try {
        let data;
        if (assetType === 'CRYPTO') {
          data = await getCryptoHistory(compareSymbol);
        } else if (assetType === 'BIST' || assetType === 'US') {
          const sym = compareSymbol.endsWith('.IS') ? compareSymbol : (assetType === 'BIST' ? `${compareSymbol}.IS` : compareSymbol);
          data = await stockService.getStockHistory(sym);
        } else if (assetType === 'FOREX') {
          data = await forexService.getForexHistory(compareSymbol);
        } else if (assetType === 'FUND') {
          data = await fundService.getFundHistory(compareSymbol);
        } else {
          return;
        }
        if (cancelled || !data?.length) return;
        let candles;
        if (assetType === 'FUND') {
          candles = data.map(c => ({
            date: c.candleDate, candleDate: c.candleDate,
            open: c.price, high: c.price, low: c.price, close: c.price,
          }));
        } else {
          candles = data.map(c => ({
            date: c.candleDate, candleDate: c.candleDate,
            open: c.open, high: c.high, low: c.low, close: c.close,
          }));
        }
        if (!cancelled) setCompareData({ candles });
      } catch {
        if (!cancelled) setCompareData(null);
      }
    };
    fetchCompare();
    return () => { cancelled = true; };
  }, [compareSymbol, assetType]);

  const presetSymbols = {
    BIST: getBistSymbols().filter(s => !isCompareOnly(s)).map(symbol => getBistDisplayName(symbol)),
    US: [
      'AAPL', 'AMD', 'AMZN', 'BAC', 'DIS',
      'GOOGL', 'GS', 'INTC', 'JNJ', 'JPM',
      'KO', 'MA', 'META', 'MSFT', 'NFLX',
      'NVDA', 'TSLA', 'V', 'WMT', 'XOM'
    ],
    CRYPTO: getCoinIds(),
    FOREX: getForexPairs(),
    FUND: fundList.map(f => f.fundCode),
    METAL: ['PAXG', 'XAUT', 'KAG']
  };
  const compareSymbols = {
    BIST: getBistSymbols().map(symbol => getBistDisplayName(symbol)),
    US: presetSymbols.US,
    CRYPTO: presetSymbols.CRYPTO,
    FOREX: presetSymbols.FOREX,
    FUND: presetSymbols.FUND,
    METAL: presetSymbols.METAL,
  };
  const filterDataByTimeRange = (candles, range) => {
    if (!candles || candles.length === 0) return candles;
    const now = new Date();
    let cutoffDate;
    switch (range) {
      case '1M': cutoffDate = new Date(now.getTime() - (30 * 24 * 60 * 60 * 1000)); break;
      case '3M': cutoffDate = new Date(now.getTime() - (90 * 24 * 60 * 60 * 1000)); break;
      case '1Y': cutoffDate = new Date(now.getTime() - (365 * 24 * 60 * 60 * 1000)); break;
      case '5Y': cutoffDate = new Date(now.getTime() - (5 * 365 * 24 * 60 * 60 * 1000)); break;
      default: return candles;
    }
    return candles.filter(candle => new Date(candle.date) >= cutoffDate);
  };
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const urlType = params.get('type');
    let urlSymbol = params.get('symbol');
    const urlRange = params.get('range');
    if (urlType && ['BIST', 'US', 'CRYPTO', 'FOREX', 'FUND', 'METAL'].includes(urlType)) {
      if (urlType === 'BIST' && urlSymbol && urlSymbol.endsWith('.IS')) {
        urlSymbol = urlSymbol.replace('.IS', '');
      }
      setAssetType(urlType);
      if (urlSymbol) setSymbol(urlSymbol);
    } else if (coinId) {
      setSymbol(coinId);
      setAssetType('CRYPTO');
    }
    if (urlRange && ['1M', '3M', '1Y', '5Y'].includes(urlRange)) setTimeRange(urlRange);
  }, [location.search]);
  const initialAssetTypeRef = useRef(assetType);
  useEffect(() => {
    if (initialAssetTypeRef.current === assetType) {
      initialAssetTypeRef.current = null; 
      return;
    }
    const symbols = presetSymbols[assetType];
    if (symbols && symbols.length > 0 && !symbols.includes(symbol)) {
      setSymbol(symbols[0]);
    }
    setCustomSymbol('');
    if ((assetType === 'CRYPTO' || assetType === 'METAL') && timeRange === '5Y') {
      setTimeRange('1Y');
    }
  }, [assetType, fundList]);
  useEffect(() => {
    if (symbol) {
      setSearchParams({ type: assetType, symbol, range: timeRange });
    }
  }, [symbol, assetType, timeRange]);
  useEffect(() => {
    if (!symbol) return;
    let cancelled = false;
    const doFetch = async () => {
      setLoading(true);
      setError(null);
      try {
        let data;
        if (assetType === 'CRYPTO') {
          data = await getCryptoHistory(symbol);
        } else if (assetType === 'BIST') {
          const symbolWithSuffix = symbol.endsWith('.IS') ? symbol : `${symbol}.IS`;
          data = await stockService.getStockHistory(symbolWithSuffix);
        } else if (assetType === 'FOREX') {
          data = await forexService.getForexHistory(symbol);
        } else if (assetType === 'FUND') {
          data = await fundService.getFundHistory(symbol);
        } else {
          if (!cancelled) { setError(`${assetType} historical data not yet implemented`); setChartData(null); }
          return;
        }
        if (cancelled) return;
        if (!data || !Array.isArray(data) || data.length === 0) {
          setError('No historical data available');
          setChartData(null);
          return;
        }
        let transformedCandles;
        if (assetType === 'FUND') {
          transformedCandles = data.map(candle => ({
            date: candle.candleDate,
            candleDate: candle.candleDate,
            price: candle.price,
            open: candle.price,
            high: candle.price,
            low: candle.price,
            close: candle.price,
            volume: null,
            investorCount: candle.investorCount,
            portfolioSize: candle.portfolioSize,
            shareCount: candle.shareCount,
            bulletinPrice: candle.bulletinPrice,
          }));
        } else {
          transformedCandles = data.map(candle => ({
            date: candle.candleDate,
            candleDate: candle.candleDate,
            price: candle.close,
            open: candle.open,
            high: candle.high,
            low: candle.low,
            close: candle.close,
            volume: candle.volume,
          }));
        }
        setChartData({ candles: transformedCandles });
      } catch (err) {
        if (!cancelled) {
          setError(err.message || 'Failed to load historical data');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    doFetch();
    return () => { cancelled = true; };
  }, [symbol, assetType, retryKey]);
  useEffect(() => {
    if (chartData && chartData.candles) {
      const filtered = filterDataByTimeRange(chartData.candles, timeRange);
      setFilteredData({ ...chartData, candles: filtered });
    }
  }, [chartData, timeRange]);

  const filteredCompareData = useMemo(() => {
    if (!compareData?.candles) return null;
    const filtered = filterDataByTimeRange(compareData.candles, timeRange);
    return filtered.length ? { candles: filtered } : null;
  }, [compareData, timeRange]);
  const handleCustomSymbol = (e) => {
    e.preventDefault();
    if (customSymbol.trim()) setSymbol(customSymbol.trim().toUpperCase());
  };
  const timeRanges = (assetType === 'BIST' || assetType === 'US' || assetType === 'FOREX' || assetType === 'FUND')
    ? ['5Y', '1Y', '3M', '1M']
    : ['1Y', '3M', '1M'];
  return (
    <motion.div
      className="py-8"
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      <div className="mx-auto max-w-7xl space-y-6">
        {}
        <motion.div variants={itemVariants} className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate(-1)}
              className="flex items-center justify-center w-10 h-10 rounded-lg border border-border-default bg-bg-base text-fg-muted hover:text-fg hover:bg-surface transition-colors duration-150"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div>
              <h1 className="flex items-center gap-3 text-2xl font-bold tracking-[-0.025em] text-fg">
                <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                    <LineChart className="w-5 h-5" />
                </span>
                Historical Analysis
              </h1>
              <p className="mt-1 text-sm text-fg-muted">
                Analyze price trends with technical indicators
              </p>
            </div>
          </div>
        </motion.div>
        {}
        <motion.div
          variants={itemVariants}
          className="rounded-xl border border-border-default bg-bg-elevated p-5 space-y-5"
        >
          {}
          <div className="space-y-2">
            <label className="text-xs font-medium uppercase tracking-wider text-fg-muted">
              Asset Type
            </label>
            <div className="flex flex-wrap gap-2">
              {['BIST', 'US', 'CRYPTO', 'FOREX', 'FUND', 'METAL'].map(type => (
                <button
                  key={type}
                  onClick={() => setAssetType(type)}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors duration-150 border ${assetType === type
                    ? 'bg-accent text-white border-accent'
                    : 'bg-bg-base text-fg-muted border-border-default hover:bg-surface hover:text-fg'
                    }`}
                >
                  {ASSET_ICONS[type]}
                  {ASSET_LABELS[type]}
                </button>
              ))}
            </div>
          </div>
          {}
          <div className="space-y-2">
            <label className="text-xs font-medium uppercase tracking-wider text-fg-muted">
              Symbol
            </label>
            <div className="flex flex-col sm:flex-row gap-3">
              <select
                value={symbol}
                onChange={(e) => setSymbol(e.target.value)}
                className="flex-1 rounded-lg border border-border-default bg-bg-elevated px-4 py-2.5 text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-accent-glow transition-all duration-200 appearance-none cursor-pointer"
              >
              {assetType === 'FUND' && fundList.length > 0 ? (
                  <>
                    {['BYF', 'YAT'].map(type => {
                      const typeFunds = fundList.filter(f => f.fundType === type);
                      if (typeFunds.length === 0) return null;
                      return (
                        <optgroup key={type} label={type === 'BYF' ? 'Borsa Yatırım Fonları' : 'Yatırım Fonları'}>
                          {typeFunds.map(f => (
                            <option key={f.fundCode} value={f.fundCode}>{f.fundCode}{f.name ? ` - ${f.name}` : ''}</option>
                          ))}
                        </optgroup>
                      );
                    })}
                  </>
                ) : (
                  presetSymbols[assetType].map(sym => (
                    <option key={sym} value={sym}>{sym}</option>
                  ))
                )}
              </select>
              <form onSubmit={handleCustomSymbol} className="flex gap-2">
                <input
                  type="text"
                  placeholder="Custom symbol..."
                  value={customSymbol}
                  onChange={(e) => setCustomSymbol(e.target.value)}
                  className="w-44 rounded-lg border border-border-default bg-bg-elevated px-4 py-2.5 text-sm text-fg placeholder:text-fg-subtle outline-none focus:border-accent focus:ring-1 focus:ring-accent-glow transition-all duration-200"
                />
                <button
                  type="submit"
                  className="rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-white hover:bg-accent-bright transition-colors duration-150"
                >
                  Go
                </button>
              </form>
            </div>
          </div>

          <div className="space-y-2">
            <label className="text-xs font-medium uppercase tracking-wider text-fg-muted">
              Compare (same type overlay)
            </label>
            <div className="flex items-center gap-3">
              <select
                value={compareSymbol || ''}
                onChange={(e) => setCompareSymbol(e.target.value || null)}
                className="flex-1 rounded-lg border border-border-default bg-bg-elevated px-4 py-2.5 text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-accent-glow transition-all duration-200 appearance-none cursor-pointer"
              >
                <option value="">-- No comparison --</option>
                {assetType === 'FUND' && fundList.length > 0 ? (
                  <>
                    {['BYF', 'YAT'].map(type => {
                      const typeFunds = fundList.filter(f => f.fundType === type && f.fundCode !== symbol);
                      if (typeFunds.length === 0) return null;
                      return (
                        <optgroup key={type} label={type === 'BYF' ? 'Borsa Yatırım Fonları' : 'Yatırım Fonları'}>
                          {typeFunds.map(f => (
                            <option key={f.fundCode} value={f.fundCode}>{f.fundCode}{f.name ? ` - ${f.name}` : ''}</option>
                          ))}
                        </optgroup>
                      );
                    })}
                  </>
                ) : (
                  (compareSymbols[assetType] || presetSymbols[assetType]).filter(s => s !== symbol).map(sym => (
                    <option key={sym} value={sym}>{sym}</option>
                  ))
                )}
              </select>
              {compareSymbol && (
                <button
                  onClick={() => setCompareSymbol(null)}
                  className="flex items-center gap-1.5 px-3 py-2.5 rounded-lg border border-red-300 text-red-500 text-sm font-medium hover:bg-red-50 dark:hover:bg-red-950/20 transition-colors duration-150"
                >
                  <X className="w-4 h-4" />
                  Remove
                </button>
              )}
            </div>
          </div>
        </motion.div>
        {}
        <motion.div variants={itemVariants} className="flex gap-4">
          {}
          <div className="flex flex-col items-center gap-2 pt-2">
            <span className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-widest text-fg-muted mb-1">
              <Clock className="w-3 h-3" />
              Time
            </span>
            {timeRanges.map(range => (
              <button
                key={range}
                onClick={() => setTimeRange(range)}
                className={`w-12 py-1.5 rounded-lg text-xs font-semibold transition-colors duration-150 border ${timeRange === range
                  ? 'bg-accent text-white border-accent'
                  : 'bg-bg-base border-border-default text-fg-muted hover:bg-surface hover:text-fg'
                  }`}
              >
                {range}
              </button>
            ))}
          </div>
          {}
          <div className="flex-1 rounded-xl border border-border-default bg-bg-elevated overflow-hidden min-h-[500px] relative">
            <AnimatePresence mode="wait">
              {loading && (
                <motion.div
                  key="loading"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="absolute inset-0 flex flex-col items-center justify-center gap-4 bg-bg-base/80 z-10"
                >
                  <Loader2 className="w-8 h-8 text-accent animate-spin" />
                  <p className="text-sm text-fg-muted">Loading historical data...</p>
                </motion.div>
              )}
              {error && !loading && (
                <motion.div
                  key="error"
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  className="absolute inset-0 flex flex-col items-center justify-center gap-4 z-10"
                >
                  <div className="flex items-center gap-2 text-danger">
                    <AlertTriangle className="w-5 h-5" />
                    <span className="text-sm font-medium">{error}</span>
                  </div>
                  <button
                    onClick={() => setRetryKey(k => k + 1)}
                    className="flex items-center gap-2 px-4 py-2 rounded-lg bg-accent text-white text-sm font-medium hover:bg-accent-bright transition-colors duration-150"
                  >
                    <RefreshCw className="w-4 h-4" />
                    Retry
                  </button>
                </motion.div>
              )}
            </AnimatePresence>
            {!loading && !error && filteredData && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.4 }}
                className="w-full h-full"
              >
                <LightweightChart
                  data={filteredData}
                  symbol={symbol}
                  assetType={assetType}
                  compareData={filteredCompareData}
                  compareSymbol={compareSymbol}
                />
              </motion.div>
            )}
            {!loading && !error && !filteredData && (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-fg-subtle">
                <BarChart2 className="w-10 h-10 opacity-40" />
                <p className="text-sm">Select a symbol to view chart data</p>
              </div>
            )}
          </div>
        </motion.div>
        {}
        <motion.div variants={itemVariants} className="space-y-4">
          <h3 className="flex items-center gap-2 text-lg font-semibold text-fg">
            <Activity className="w-5 h-5 text-fg-subtle" />
            About Technical Indicators
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {[
              {
                icon: TrendingUp,
                title: 'SMA 20 (Simple Moving Average)',
                desc: '20-day average price. Short-term trend indicator. Price above SMA20 suggests uptrend.',
              },
              {
                icon: LineChart,
                title: 'EMA 50 (Exponential Moving Average)',
                desc: '50-day weighted average. Reacts faster to recent price changes than SMA.',
              },
              {
                icon: BarChart2,
                title: 'Drawing Tools',
                desc: 'Use trend lines, Fibonacci retracements, and freehand drawings for custom analysis.',
              },
            ].map((card, i) => {
              const Icon = card.icon;
              return (
                <motion.div
                  key={card.title}
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.3 + i * 0.1, duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
                  className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                >
                  <div className="flex items-center gap-3 mb-3">
                    <Icon className="w-5 h-5 text-fg-subtle group-hover:text-accent transition-colors duration-150" />
                    <h4 className="text-sm font-semibold text-fg">{card.title}</h4>
                  </div>
                  <p className="text-sm leading-relaxed text-fg-muted">{card.desc}</p>
                </motion.div>
              );
            })}
          </div>
        </motion.div>
      </div>
    </motion.div>
  );
};
export default ChartView;
