import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Bitcoin,
    TrendingUp,
    RefreshCw,
    BarChart2,
    LineChart,
    Download,
    Wrench,
    Loader2,
    AlertTriangle,
    Activity,
    Clock,
    ArrowUpRight,
    ArrowDownRight,
} from 'lucide-react';
import { getMultipleCryptos, adminService } from '../services/marketService';
import { getCoinIds, getCoinIcon, getCoinIdBySymbol } from '../constants/coins';
import { useAuth } from '../context/AuthContext';
const container = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.06 } },
};
const card = {
    hidden: { opacity: 0, y: 24 },
    show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};
function Crypto() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [cryptos, setCryptos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
    });
    const isAdmin = hasRole('ADMIN');
    console.log('[Crypto] isAdmin:', isAdmin);
    useEffect(() => {
        fetchCryptos();
    }, []);
    const fetchCryptos = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await getMultipleCryptos(getCoinIds());
            setCryptos(data);
        } catch (err) {
            setError('Kripto para verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };
    const handleCryptoSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerCryptoSnapshot();
            alert(response.message || 'Kripto snapshot güncelleme başlatıldı');
            setTimeout(fetchCryptos, 5000);
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };
    const handleCryptoCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerCryptoCandles();
            alert(response.message || 'Kripto candle güncelleme başlatıldı');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };
    const handleCryptoFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerCryptoFull();
            alert(response.message || 'Kripto tam güncelleme başlatıldı');
            setTimeout(fetchCryptos, 10000);
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
    const formatPrice = (price, currency = 'USD') => {
        if (price === null || price === undefined) return 'N/A';
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: currency,
            minimumFractionDigits: 2,
            maximumFractionDigits: currency === 'USD' ? 2 : 8
        }).format(price);
    };
    const formatPriceTRY = (price) => {
        if (price === null || price === undefined) return 'N/A';
        return new Intl.NumberFormat('tr-TR', {
            style: 'currency',
            currency: 'TRY',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    };
    const formatCompactNumber = (number) => {
        if (number === null || number === undefined) return 'N/A';
        return new Intl.NumberFormat('en-US', {
            notation: 'compact',
            compactDisplay: 'short',
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 1,
            maximumFractionDigits: 2
        }).format(number);
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
                    <span className="text-fg-muted text-sm">Kripto verileri yükleniyor…</span>
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
                    onClick={fetchCryptos}
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
                        <Bitcoin className="h-5 w-5" />
                    </span>
                    Kripto Paralar
                </h1>
                <div className="flex flex-wrap items-center gap-2">
                    <button
                        onClick={fetchCryptos}
                        disabled={loading}
                        className="flex items-center gap-2 rounded-md border border-border-default bg-bg-base px-4 py-2 text-sm text-fg-muted transition-colors duration-150 hover:bg-surface hover:text-fg disabled:opacity-50"
                    >
                        <RefreshCw className="h-4 w-4" />
                        Yenile
                    </button>
                    {isAdmin && (
                        <>
                            <button
                                onClick={handleCryptoSnapshotUpdate}
                                disabled={updating.snapshot || loading}
                                title="Kripto snapshot verilerini güncelle (fiyat, hacim vb.)"
                                className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                            >
                                {updating.snapshot ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                                Snapshot
                            </button>
                            <button
                                onClick={handleCryptoCandlesUpdate}
                                disabled={updating.candles || loading}
                                title="Kripto mum verilerini güncelle (OHLC)"
                                className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                            >
                                {updating.candles ? <Loader2 className="h-4 w-4 animate-spin" /> : <LineChart className="h-4 w-4" />}
                                Candles
                            </button>
                            <button
                                onClick={handleCryptoFullUpdate}
                                disabled={updating.full || loading}
                                title="Tam güncelleme (snapshot + candles)"
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
            <AnimatePresence>
                {cryptos.length > 0 && (
                    <motion.div
                        variants={container}
                        initial="hidden"
                        animate="show"
                        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                    >
                        {cryptos.map((crypto) => {
                            const cls = getChangeClass(crypto.changePercent);
                            return (
                                <motion.div
                                    key={crypto.id}
                                    variants={card}
                                    className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                >
                                    {}
                                    <div className="flex items-start justify-between">
                                        <div className="flex items-center gap-3">
                                            <span className="text-2xl">{getCoinIcon(crypto.symbol)}</span>
                                            <div className="min-w-0">
                                                <h3 className="truncate text-sm font-semibold text-fg">{crypto.symbol}</h3>
                                                <span className="block truncate text-xs text-fg-muted">{crypto.name}</span>
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <span className="rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
                                                #{crypto.symbol}
                                            </span>
                                            <button
                                                onClick={(e) => {
                                                    e.preventDefault();
                                                    e.stopPropagation();
                                                    const coinId = getCoinIdBySymbol(crypto.symbol);
                                                    navigate(`/charts?type=CRYPTO&symbol=${coinId}&range=1M`);
                                                }}
                                                title="Grafiği Görüntüle"
                                                className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                            >
                                                <BarChart2 className="h-3.5 w-3.5 text-fg-subtle group-hover:text-accent transition-colors duration-150" />
                                            </button>
                                        </div>
                                    </div>
                                    {}
                                    <div className="mt-3 space-y-1">
                                        <span className="font-mono text-xl font-bold text-fg">
                                            {formatPrice(crypto.currentPrice, 'USD')}
                                        </span>
                                        <div className="flex items-center gap-2 text-xs text-fg-muted">
                                            <span className="font-medium">TRY</span>
                                            <span className="font-mono">{formatPriceTRY(crypto.currentPriceTry)}</span>
                                        </div>
                                    </div>
                                    {}
                                    {crypto.changePercent !== null && crypto.changePercent !== undefined && (
                                        <div className={`mt-2 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                            {crypto.changePercent > 0 ? (
                                                <ArrowUpRight className="h-3.5 w-3.5" />
                                            ) : crypto.changePercent < 0 ? (
                                                <ArrowDownRight className="h-3.5 w-3.5" />
                                            ) : null}
                                            {Math.abs(crypto.changePercent).toFixed(2)}%
                                            <span className="ml-1 opacity-75">24h</span>
                                        </div>
                                    )}
                                    {}
                                    <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="flex items-center gap-1 text-fg-muted">
                                                <Activity className="h-3 w-3" />
                                                Change
                                            </span>
                                            <span className="font-mono text-fg">{formatPrice(crypto.changeAmount, crypto.currency)}</span>
                                        </div>
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="flex items-center gap-1 text-fg-muted">
                                                <BarChart2 className="h-3 w-3" />
                                                Volume
                                            </span>
                                            <span className="font-mono text-fg">{formatCompactNumber(crypto.totalVolume)}</span>
                                        </div>
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="flex items-center gap-1 text-fg-muted">
                                                <TrendingUp className="h-3 w-3" />
                                                Market Cap
                                            </span>
                                            <span className="font-mono text-fg">{formatCompactNumber(crypto.marketCap)}</span>
                                        </div>
                                    </div>
                                    {}
                                    <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                                        <Clock className="h-3 w-3" />
                                        {crypto.lastUpdated ? new Date(crypto.lastUpdated).toLocaleString('tr-TR') : 'N/A'}
                                    </div>
                                </motion.div>
                            );
                        })}
                    </motion.div>
                )}
            </AnimatePresence>
            {}
            {cryptos.length === 0 && (
                <motion.div
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col items-center justify-center gap-2 rounded-lg border border-border-default bg-bg-base py-14"
                >
                    <Bitcoin className="h-7 w-7 text-fg-subtle" />
                    <p className="text-sm text-fg-muted">
                        Henüz kripto para verisi yok.
                    </p>
                    <p className="text-xs text-fg-subtle">
                        {isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : 'Admin veri güncellemesini bekleyin.'}
                    </p>
                </motion.div>
            )}
        </div>
    );
}
export default Crypto;
