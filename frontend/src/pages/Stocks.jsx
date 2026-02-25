import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    TrendingUp,
    TrendingDown,
    RefreshCw,
    BarChart2,
    LineChart,
    Download,
    Wrench,
    Loader2,
    AlertTriangle,
    Info,
    ChevronUp,
    ChevronDown,
    Activity,
    Clock,
} from 'lucide-react';
import { stockService, adminService } from '../services/marketService';
import { getBistSymbols } from '../constants/stocks';
import { useAuth } from '../context/AuthContext';
const container = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.05 } },
};
const card = {
    hidden: { opacity: 0, y: 24 },
    show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};
function Stocks() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [stocks, setStocks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
    });
    const isAdmin = hasRole('ADMIN');
    console.log('[Stocks] isAdmin:', isAdmin);
    useEffect(() => {
        console.log('[Stocks] useEffect - fetching stocks');
        fetchStocks();
    }, []);
    const fetchStocks = async () => {
        console.log('[Stocks] fetchStocks() called');
        setLoading(true);
        setError(null);
        try {
            const symbols = getBistSymbols();
            console.log('[Stocks] Fetching stocks for symbols:', symbols);
            const data = await stockService.getMultipleStocks(symbols);
            console.log('[Stocks] fetchStocks() success, data:', data);
            setStocks(data || []);
        } catch (err) {
            console.error('[Stocks] fetchStocks() error:', err);
            setError('Hisse senedi verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };
    const indices = stocks.filter(s => ['XU030.IS', 'XU100.IS', 'XU500.IS'].includes(s.symbol));
    const regularStocks = stocks.filter(s => !['XU030.IS', 'XU100.IS', 'XU500.IS'].includes(s.symbol));
    const handleStockSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerStockSnapshot();
            alert(response.message || 'Hisse snapshot güncelleme başlatıldı');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };
    const handleStockCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerStockCandles();
            alert(response.message || 'Hisse candle güncelleme başlatıldı (Bu işlem 10-15 dakika sürebilir)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };
    const handleStockFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerStockFull();
            alert(response.message || 'Hisse tam güncelleme başlatıldı (Bu işlem 15-20 dakika sürebilir)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, full: false }));
        }
    };
    const getChangeClass = (change) => {
        if (change > 0) return 'positive';
        if (change < 0) return 'negative';
        return 'neutral';
    };
    const formatPrice = (price) => {
        if (price === null || price === undefined) return 'N/A';
        return new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    };
    const formatVolume = (volume) => {
        if (!volume) return 'N/A';
        if (volume >= 1000000) {
            return `${(volume / 1000000).toFixed(1)}M`;
        } else if (volume >= 1000) {
            return `${(volume / 1000).toFixed(1)}K`;
        }
        return volume;
    };
    const changeColors = {
        positive: 'text-success',
        negative: 'text-danger',
        neutral: 'text-fg-muted',
    };
    const changeBg = {
        positive: 'bg-success/10',
        negative: 'bg-danger/10',
        neutral: 'bg-fg-muted/10',
    };
        if (loading) {
        return (
            <div className="flex min-h-[60vh] items-center justify-center">
                <motion.div
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="flex flex-col items-center gap-3"
                >
                    <Loader2 className="h-8 w-8 animate-spin text-accent" />
                    <span className="text-fg-muted text-sm">Hisse verileri yükleniyor…</span>
                </motion.div>
            </div>
        );
    }
        if (error) {
        return (
            <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4">
                <motion.div
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col items-center gap-3 rounded-lg border border-danger/30 bg-danger/5 px-6 py-5"
                >
                    <AlertTriangle className="h-7 w-7 text-danger" />
                    <p className="text-fg text-sm">{error}</p>
                </motion.div>
                <button
                    onClick={fetchStocks}
                    className="flex items-center gap-2 rounded-md border border-border-default bg-bg-base px-4 py-2 text-sm text-fg transition-colors duration-150 hover:bg-surface"
                >
                    <RefreshCw className="h-4 w-4" />
                    Tekrar Dene
                </button>
            </div>
        );
    }
        return (
        <div className="space-y-6 py-6">
            {}
            <motion.div
                initial={{ opacity: 0, y: -16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
                className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
            >
                <h1 className="flex items-center gap-2.5 text-2xl font-bold tracking-[-0.025em] text-fg sm:text-3xl">
                    <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                        <Activity className="h-5 w-5" />
                    </span>
                    Borsa İstanbul (BIST)
                </h1>
                <div className="flex flex-wrap items-center gap-2">
                    <button
                        onClick={fetchStocks}
                        disabled={loading}
                        className="flex items-center gap-2 rounded-md border border-border-default bg-bg-base px-4 py-2 text-sm text-fg-muted transition-colors duration-150 hover:bg-surface hover:text-fg disabled:opacity-50"
                    >
                        <RefreshCw className="h-4 w-4" />
                        Yenile
                    </button>
                    {isAdmin && (
                        <>
                            <button
                                onClick={handleStockSnapshotUpdate}
                                disabled={updating.snapshot || loading}
                                title="Hisse snapshot verilerini güncelle (fiyat, hacim vb.)"
                                className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                            >
                                {updating.snapshot ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                                Snapshot
                            </button>
                            <button
                                onClick={handleStockCandlesUpdate}
                                disabled={updating.candles || loading}
                                title="5 yıllık OHLC verilerini güncelle (10-15 dakika)"
                                className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                            >
                                {updating.candles ? <Loader2 className="h-4 w-4 animate-spin" /> : <LineChart className="h-4 w-4" />}
                                Candles (5y)
                            </button>
                            <button
                                onClick={handleStockFullUpdate}
                                disabled={updating.full || loading}
                                title="Tam güncelleme (snapshot + 5y candles, 15-20 dakika)"
                                className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                            >
                                {updating.full ? <Loader2 className="h-4 w-4 animate-spin" /> : <Wrench className="h-4 w-4" />}
                                Full Update
                            </button>
                        </>
                    )}
                </div>
            </motion.div>
            {}
            {stocks.length > 0 && indices.length === 0 && (
                <motion.div
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex items-start gap-3 rounded-lg border border-warning/30 bg-warning/5 px-4 py-3"
                >
                    <Info className="mt-0.5 h-5 w-5 shrink-0 text-warning" />
                    <p className="text-sm text-fg-muted">
                        Endeks verileri bulunamadı. Admin snapshot güncellemesi ile endeksler (BIST 30, BIST 100, BIST 500) çekilebilir.
                    </p>
                </motion.div>
            )}
            {}
            <AnimatePresence>
                {indices.length > 0 && (
                    <motion.section
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="space-y-3"
                    >
                        <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
                            <BarChart2 className="h-5 w-5 text-fg-subtle" />
                            BIST Endeksleri
                        </h2>
                        <motion.div
                            variants={container}
                            initial="hidden"
                            animate="show"
                            className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
                        >
                            {indices.map((index) => {
                                const cls = getChangeClass(index.priceChangePercent);
                                return (
                                    <motion.div
                                        key={index.symbol}
                                        variants={card}
                                        className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                    >
                                        <div className="flex items-start justify-between">
                                            <div>
                                                <h3 className="text-base font-semibold text-fg">
                                                    {index.symbol === 'XU030.IS' ? 'BIST 30' :
                                                        index.symbol === 'XU100.IS' ? 'BIST 100' :
                                                            'BIST 500'}
                                                </h3>
                                                <span className="text-xs text-fg-muted">{index.symbol}</span>
                                            </div>
                                            <button
                                                onClick={() => {
                                                    navigate(`/charts?type=BIST&symbol=${index.symbol}&range=3M`);
                                                }}
                                                title="Grafiği Görüntüle"
                                                className="flex h-8 w-8 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                            >
                                                <BarChart2 className="h-4 w-4 text-fg-subtle group-hover:text-accent transition-colors duration-150" />
                                            </button>
                                        </div>
                                        <p className="mt-3 font-mono text-2xl font-bold text-fg">
                                            {formatPrice(index.currentPrice)}
                                        </p>
                                        <div className={`mt-2 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                            {index.priceChangePercent > 0 ? (
                                                <ChevronUp className="h-3.5 w-3.5" />
                                            ) : index.priceChangePercent < 0 ? (
                                                <ChevronDown className="h-3.5 w-3.5" />
                                            ) : null}
                                            {Math.abs(index.priceChangePercent || 0).toFixed(2)}%
                                        </div>
                                    </motion.div>
                                );
                            })}
                        </motion.div>
                    </motion.section>
                )}
            </AnimatePresence>
            {}
            {regularStocks.length > 0 && (
                <motion.section
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: 0.15 }}
                    className="space-y-3"
                >
                    <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
                        <TrendingUp className="h-5 w-5 text-fg-subtle" />
                        BIST Hisse Senetleri
                        <span className="ml-1 text-sm font-normal text-fg-muted">({regularStocks.length} hisse)</span>
                    </h2>
                    <motion.div
                        variants={container}
                        initial="hidden"
                        animate="show"
                        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                    >
                        {regularStocks.map((stock) => {
                            const cls = getChangeClass(stock.priceChangePercent);
                            return (
                                <motion.div
                                    key={stock.symbol}
                                    variants={card}
                                    className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                >
                                    {}
                                    <div className="flex items-start justify-between">
                                        <div className="min-w-0 flex-1">
                                            <h3 className="truncate text-sm font-semibold text-fg">{stock.symbol}</h3>
                                            <span className="block truncate text-xs text-fg-muted">{stock.name}</span>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <span className="rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
                                                {stock.exchange || 'BIST'}
                                            </span>
                                            <button
                                                onClick={() => {
                                                    navigate(`/charts?type=BIST&symbol=${stock.symbol}&range=3M`);
                                                }}
                                                title="Grafiği Görüntüle"
                                                className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                            >
                                                <BarChart2 className="h-3.5 w-3.5 text-fg-subtle group-hover:text-accent transition-colors duration-150" />
                                            </button>
                                        </div>
                                    </div>
                                    {}
                                    <div className="mt-3">
                                        <p className="font-mono text-xl font-bold text-fg">
                                            ₺{formatPrice(stock.currentPrice)}
                                        </p>
                                        <div className={`mt-1 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                            {stock.priceChangePercent > 0 ? (
                                                <TrendingUp className="h-3.5 w-3.5" />
                                            ) : stock.priceChangePercent < 0 ? (
                                                <TrendingDown className="h-3.5 w-3.5" />
                                            ) : null}
                                            {Math.abs(stock.priceChangePercent || 0).toFixed(2)}%
                                            <span className="ml-1 opacity-75">
                                                ({stock.priceChangeAmount > 0 ? '+' : ''}₺{formatPrice(stock.priceChangeAmount)})
                                            </span>
                                        </div>
                                    </div>
                                    {}
                                    <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                                        {stock.openPrice != null && (
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="text-fg-muted">Açılış</span>
                                                <span className="font-mono text-fg">₺{formatPrice(stock.openPrice)}</span>
                                            </div>
                                        )}
                                        {stock.dayHigh != null && (
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="flex items-center gap-1 text-fg-muted">
                                                    <ChevronUp className="h-3 w-3 text-success" />
                                                    En Yüksek
                                                </span>
                                                <span className="font-mono text-fg">₺{formatPrice(stock.dayHigh)}</span>
                                            </div>
                                        )}
                                        {stock.dayLow != null && (
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="flex items-center gap-1 text-fg-muted">
                                                    <ChevronDown className="h-3 w-3 text-danger" />
                                                    En Düşük
                                                </span>
                                                <span className="font-mono text-fg">₺{formatPrice(stock.dayLow)}</span>
                                            </div>
                                        )}
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="text-fg-muted">Hacim</span>
                                            <span className="font-mono text-fg">{formatVolume(stock.volume)}</span>
                                        </div>
                                    </div>
                                    {}
                                    <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                                        <Clock className="h-3 w-3" />
                                        {stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleString('tr-TR') : 'N/A'}
                                    </div>
                                </motion.div>
                            );
                        })}
                    </motion.div>
                </motion.section>
            )}
            {}
            {regularStocks.length === 0 && stocks.length === 0 && (
                <motion.div
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col items-center justify-center gap-2 rounded-lg border border-border-default bg-bg-base py-14"
                >
                    <BarChart2 className="h-7 w-7 text-fg-subtle" />
                    <p className="text-sm text-fg-muted">
                        Henüz hisse senedi verisi yok.
                    </p>
                    <p className="text-xs text-fg-subtle">
                        {isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : 'Admin veri güncellemesini bekleyin.'}
                    </p>
                </motion.div>
            )}
        </div>
    );
}
export default Stocks;
