import { useState, useEffect, useRef, useMemo } from 'react';
import { useSearchParams, useParams, useNavigate, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LineChart, ArrowLeft, BarChart2, Loader2,
  AlertTriangle, TrendingUp, RefreshCw, Activity
} from 'lucide-react';
import LightweightChart from './LightweightChart';
import CompareBar from '../../shared/components/CompareBar';
import { getCryptoHistory, stockService, forexService, fundService, trackedAssetService } from '../../shared/services/marketService';
import { formatBistSymbol } from '../../shared/constants/stocks';
import { getForexPairs } from '../../shared/constants/forex';


const ASSET_ICONS = {
  BIST: <BarChart2 className="w-4 h-4" />,
  CRYPTO: <Activity className="w-4 h-4" />,
  FOREX: <RefreshCw className="w-4 h-4" />,
  FUND: <LineChart className="w-4 h-4" />,
};
const ASSET_LABELS = {
  BIST: 'BIST',
  CRYPTO: 'Kripto',
  FOREX: 'Döviz',
  FUND: 'Fon',
};
const ROUTE_TO_ASSET_TYPE = {
  bist: 'BIST',
  crypto: 'CRYPTO',
  forex: 'FOREX',
  fund: 'FUND',
};
const ASSET_TYPE_TO_ROUTE = {
  BIST: 'bist',
  CRYPTO: 'crypto',
  FOREX: 'forex',
  FUND: 'fund',
};
const ensureBistSuffix = (code) => (code.endsWith('.IS') ? code : `${code}.IS`);
const HISTORY_FETCHERS = {
  CRYPTO: (code, range) => getCryptoHistory(code, range),
  STOCK: (code, range) => stockService.getHistory(ensureBistSuffix(code), range),
  BIST: (code, range) => stockService.getHistory(ensureBistSuffix(code), range),
  US: (code, range) => stockService.getHistory(ensureBistSuffix(code), range),
  FOREX: (code, range) => forexService.getHistory(code, range),
  FUND: (code, range) => fundService.getHistory(code, range),
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
  const { coinId, assetType: routeAssetType, symbol: routeSymbol } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const normalizedRouteAssetType = routeAssetType?.toLowerCase();
  const routeType = normalizedRouteAssetType ? ROUTE_TO_ASSET_TYPE[normalizedRouteAssetType] : null;
  const routeSymbolValue = routeSymbol ? decodeURIComponent(routeSymbol) : null;
  const isDetailRoute = Boolean(routeType && routeSymbolValue);
  const [error, setError] = useState(null);
  const [assetType, setAssetType] = useState(() => {
    if (routeType) return routeType;
    const params = new URLSearchParams(window.location.search);
    const urlType = params.get('type');
    if (urlType && ['BIST', 'CRYPTO', 'FOREX', 'FUND'].includes(urlType)) return urlType;
    return 'CRYPTO';
  });
  const [symbol, setSymbol] = useState(() => {
    if (routeType && routeSymbolValue) {
      if (routeType === 'BIST' && routeSymbolValue.endsWith('.IS')) return routeSymbolValue.replace('.IS', '');
      return routeSymbolValue;
    }
    const params = new URLSearchParams(window.location.search);
    const urlType = params.get('type');
    let sym = params.get('symbol') || coinId || 'bitcoin';
    if (urlType === 'BIST' && sym.endsWith('.IS')) sym = sym.replace('.IS', '');
    return sym;
  });
  const [timeRange, setTimeRange] = useState('6M');
  const [customSymbol, setCustomSymbol] = useState('');
  const [compareAsset, setCompareAsset] = useState(null);

  const { data: trackedCrypto = [] } = useQuery({
    queryKey: ['trackedAssets', 'CRYPTO'],
    queryFn: () => trackedAssetService.getByType('CRYPTO'),
    staleTime: 30_000,
  });
  const { data: trackedStocks = [] } = useQuery({
    queryKey: ['trackedAssets', 'STOCK'],
    queryFn: () => trackedAssetService.getByType('STOCK'),
    staleTime: 30_000,
  });
  const { data: trackedFunds = [] } = useQuery({
    queryKey: ['trackedAssets', 'FUND'],
    queryFn: () => trackedAssetService.getByType('FUND'),
    staleTime: 30_000,
  });
  const trackedUniverse = useMemo(() => {
    const universe = {
      CRYPTO: [...(trackedCrypto || [])],
      BIST: [...(trackedStocks || [])],
      FUND: [...(trackedFunds || [])],
    };
    return universe;
  }, [trackedCrypto, trackedStocks, trackedFunds]);

  const { data: fundListRaw = [] } = useQuery({
    queryKey: ['fundList'],
    queryFn: fundService.getAll,
    enabled: assetType === 'FUND',
    staleTime: 60_000,
  });
  const fundList = fundListRaw || [];

  useEffect(() => {
    setCompareAsset(null);
  }, [assetType, symbol]);

  const singleAssetType = { CRYPTO: 'CRYPTO', BIST: 'STOCK', FUND: 'FUND' }[assetType];
  const singleCode = symbol && singleAssetType
    ? (assetType === 'BIST' ? formatBistSymbol(symbol) : symbol)
    : null;
  const singleKey = { CRYPTO: 'CRYPTO', BIST: 'BIST', FUND: 'FUND' }[assetType];
  const alreadyTracked = singleCode && singleKey
    ? (trackedUniverse[singleKey] || []).some(item => item.assetCode === singleCode)
    : true;

  const { data: singleTracked } = useQuery({
    queryKey: ['trackedAsset', singleAssetType, singleCode],
    queryFn: () => trackedAssetService.getOne(singleAssetType, singleCode),
    enabled: !!singleCode && !alreadyTracked,
    staleTime: 60_000,
  });

  const { data: compareRaw } = useQuery({
    queryKey: ['chartCompare', compareAsset?.type, compareAsset?.code, timeRange],
    queryFn: () => {
      const fetcher = HISTORY_FETCHERS[compareAsset.type];
      return fetcher ? fetcher(compareAsset.code, timeRange) : Promise.resolve([]);
    },
    enabled: !!compareAsset,
    placeholderData: (prev) => prev,
  });

  const compareData = useMemo(() => {
    if (!compareRaw?.length) return null;
    return { candles: compareRaw };
  }, [compareRaw]);

  const compareSymbol = compareAsset?.code || null;

  const trackedBistSymbols = trackedUniverse.BIST
    .filter(item => !item.compareOnly)
    .map(item => item.assetCode.replace('.IS', ''));
  const trackedCryptoSymbols = trackedUniverse.CRYPTO.map(item => item.assetCode);
  const trackedFundSymbols = trackedUniverse.FUND.map(item => item.assetCode);

  const presetSymbols = {
    BIST: trackedBistSymbols,
    CRYPTO: trackedCryptoSymbols,
    FOREX: getForexPairs(),
    FUND: trackedFundSymbols.length > 0 ? trackedFundSymbols : fundList.map(f => f.fundCode),
  };
  useEffect(() => {
    if (routeType && routeSymbolValue) {
      const nextSymbol = routeType === 'BIST' && routeSymbolValue.endsWith('.IS')
        ? routeSymbolValue.replace('.IS', '')
        : routeSymbolValue;
      setAssetType(routeType);
      setSymbol(nextSymbol);
      return;
    }

    const params = new URLSearchParams(window.location.search);
    const urlType = params.get('type');
    let urlSymbol = params.get('symbol');
    if (urlType && ['BIST', 'CRYPTO', 'FOREX', 'FUND'].includes(urlType)) {
      if (urlType === 'BIST' && urlSymbol && urlSymbol.endsWith('.IS')) {
        urlSymbol = urlSymbol.replace('.IS', '');
      }
      setAssetType(urlType);
      if (urlSymbol) setSymbol(urlSymbol);
    } else if (coinId) {
      setSymbol(coinId);
      setAssetType('CRYPTO');
    }
  }, [location.search, routeType, routeSymbolValue, coinId, searchParams]);
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
  }, [assetType, fundList]);
  useEffect(() => {
    if (symbol) {
      if (isDetailRoute) {
        const routeType = ASSET_TYPE_TO_ROUTE[assetType] || 'crypto';
        const routeSymbol = encodeURIComponent(symbol);
        navigate(`/charts/${routeType}/${routeSymbol}`, { replace: true });
        return;
      }
      setSearchParams({ type: assetType, symbol });
    }
  }, [symbol, assetType, isDetailRoute, navigate, setSearchParams]);
  const fetchSymbol = assetType === 'BIST' && symbol && !symbol.endsWith('.IS') ? `${symbol}.IS` : symbol;

  const fetchHistory = (sym, type, range) => {
    const fetcher = HISTORY_FETCHERS[type];
    return fetcher ? fetcher(sym, range) : Promise.resolve([]);
  };

  const { data: historyRaw, isLoading: loading, refetch: refetchHistory } = useQuery({
    queryKey: ['chartHistory', assetType, fetchSymbol, timeRange],
    queryFn: () => fetchHistory(symbol, assetType, timeRange),
    enabled: !!symbol,
    placeholderData: (prev) => prev,
  });

  const chartData = useMemo(() => {
    if (!historyRaw || !Array.isArray(historyRaw) || historyRaw.length === 0) return null;
    return { candles: historyRaw };
  }, [historyRaw]);
  const handleCustomSymbol = (e) => {
    e.preventDefault();
    if (customSymbol.trim()) setSymbol(customSymbol.trim().toUpperCase());
  };
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
                {ASSET_LABELS[assetType]} - {symbol} detail chart and technical analysis
              </p>
            </div>
          </div>
        </motion.div>
        {}
        <motion.div
          variants={itemVariants}
          className="rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-5 space-y-5 overflow-visible relative z-10"
        >
          {isDetailRoute ? (
            <div className="rounded-lg border border-border-default bg-bg-base px-4 py-3">
              <p className="text-xs uppercase tracking-wider text-fg-muted">Detail Mode</p>
              <p className="mt-1 text-sm text-fg">
                This page is locked to <span className="font-semibold">{ASSET_LABELS[assetType]}</span> / <span className="font-semibold">{symbol}</span>.
              </p>
            </div>
          ) : (
            <>
              <div className="space-y-2">
                <label className="text-xs font-medium uppercase tracking-wider text-fg-muted">
                  Asset Type
                </label>
                <div className="flex flex-wrap gap-2">
                  {['BIST', 'CRYPTO', 'FOREX', 'FUND'].map(type => (
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
            </>
          )}

          <div className="space-y-2">
            <label className="text-xs font-medium uppercase tracking-wider text-fg-muted">
              Karşılaştır
            </label>
            <CompareBar
              compareAsset={compareAsset}
              onSelect={setCompareAsset}
              onClear={() => setCompareAsset(null)}
              excludeCodes={[symbol, fetchSymbol].filter(Boolean)}
            />
          </div>
        </motion.div>
        {}
        <motion.div variants={itemVariants} className="flex gap-4">
          {}
          {}
          <div className="flex-1 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md overflow-hidden min-h-[500px] relative">
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
                    onClick={() => refetchHistory()}
                    className="flex items-center gap-2 px-4 py-2 rounded-lg bg-accent text-white text-sm font-medium hover:bg-accent-bright transition-colors duration-150"
                  >
                    <RefreshCw className="w-4 h-4" />
                    Retry
                  </button>
                </motion.div>
              )}
            </AnimatePresence>
            {!loading && !error && chartData && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.4 }}
                className="w-full h-full"
              >
                <LightweightChart
                  data={chartData}
                  symbol={symbol}
                  assetType={assetType}
                  compareData={compareData}
                  compareSymbol={compareSymbol}
                  timeRange={timeRange}
                  onTimeRangeChange={setTimeRange}
                />
              </motion.div>
            )}
            {!loading && !error && !chartData && (
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
