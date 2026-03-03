import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Bitcoin,
    TrendingUp,
    BarChart2,
    Activity,
    Clock,
    ArrowUpRight,
    ArrowDownRight,
} from 'lucide-react';
import { getMultipleCryptos, adminService } from '../services/marketService';
import { getCoinIds, getCoinIcon, getCoinIdBySymbol } from '../constants/coins';
import { useAuth } from '../context/AuthContext';
import { getChangeClass, changeColors, changeBg, formatPriceUSD, formatPriceTRY, formatCompactNumber } from '../utils/formatters';
import { containerVariants, cardVariants } from '../utils/animations';
import LoadingState from '../components/LoadingState';
import ErrorState from '../components/ErrorState';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
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
    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'Kripto snapshot verilerini güncelle (fiyat, hacim vb.)', handler: handleCryptoSnapshotUpdate },
        { key: 'candles', label: 'Candles', title: 'Kripto mum verilerini güncelle (OHLC)', handler: handleCryptoCandlesUpdate },
        { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + candles)', handler: handleCryptoFullUpdate },
    ];
    if (loading) return <LoadingState message="Kripto verileri yükleniyor…" />;
    if (error) return <ErrorState message={error} onRetry={fetchCryptos} />;
        return (
        <div className="space-y-6 py-6">
            {}
            <PageHeader
                icon={<Bitcoin className="h-5 w-5" />}
                title="Kripto Paralar"
                onRefresh={fetchCryptos}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />
            {}
            <AnimatePresence>
                {cryptos.length > 0 && (
                    <motion.div
                        variants={containerVariants(0.06)}
                        initial="hidden"
                        animate="show"
                        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                    >
                        {cryptos.map((crypto) => {
                            const cls = getChangeClass(crypto.changePercent);
                            return (
                                <motion.div
                                    key={crypto.id}
                                    variants={cardVariants}
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
                                            {formatPriceUSD(crypto.currentPrice)}
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
                                            <span className="font-mono text-fg">{formatPriceUSD(crypto.changeAmount)}</span>
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
                <EmptyState
                    icon={<Bitcoin className="h-7 w-7 text-fg-subtle" />}
                    message="Henüz kripto para verisi yok."
                    hint={isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : 'Admin veri güncellemesini bekleyin.'}
                />
            )}
        </div>
    );
}
export default Crypto;
