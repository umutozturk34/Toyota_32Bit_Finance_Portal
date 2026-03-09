import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    TrendingUp,
    TrendingDown,
    BarChart2,
    Info,
    ChevronUp,
    ChevronDown,
    Activity,
    Clock,
} from 'lucide-react';
import { stockService, adminService } from '../services/marketService';
import { getBistSymbols, getIndexLongName, isMainIndex, isSecondaryIndex, isIndex } from '../constants/stocks';
import { useAuth } from '../context/AuthContext';
import { getChangeClass, changeColors, changeBg, formatPrice, formatVolume } from '../utils/formatters';
import { containerVariants, cardVariants } from '../utils/animations';
import LoadingState from '../components/LoadingState';
import ErrorState from '../components/ErrorState';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
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
    const indices = stocks.filter(s => isMainIndex(s.symbol));
    const secondaryIndices = stocks.filter(s => isSecondaryIndex(s.symbol));
    const regularStocks = stocks.filter(s => !isIndex(s.symbol));
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
    const formatStockPrice = (price) => formatPrice(price, { locale: 'tr-TR' });

    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'Hisse snapshot verilerini güncelle (fiyat, hacim vb.)', handler: handleStockSnapshotUpdate },
        { key: 'candles', label: 'Candles (5y)', title: '5 yıllık OHLC verilerini güncelle (10-15 dakika)', handler: handleStockCandlesUpdate },
        { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + 5y candles, 15-20 dakika)', handler: handleStockFullUpdate },
    ];
    if (loading) return <LoadingState message="Hisse verileri yükleniyor…" />;
    if (error) return <ErrorState message={error} onRetry={fetchStocks} />;
        return (
        <div className="space-y-6 py-6">
            {}
            <PageHeader
                icon={<Activity className="h-5 w-5" />}
                title="Borsa İstanbul (BIST)"
                onRefresh={fetchStocks}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />
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
                            variants={containerVariants()}
                            initial="hidden"
                            animate="show"
                            className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
                        >
                            {indices.map((index) => {
                                const cls = getChangeClass(index.priceChangePercent);
                                const displayName = index.symbol === 'XU030.IS' ? 'BIST 30' :
                                    index.symbol === 'XU100.IS' ? 'BIST 100' : 'BIST 500';
                                return (
                                    <motion.div
                                        key={index.symbol}
                                        variants={cardVariants}
                                        className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                    >
                                        <div className="flex items-start justify-between">
                                            <div>
                                                <h3 className="text-base font-semibold text-fg">
                                                    {displayName}
                                                </h3>
                                                <span className="text-xs text-fg-muted">{getIndexLongName(index.symbol)}</span>
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
                                            {formatStockPrice(index.currentPrice)}
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
                        {secondaryIndices.length > 0 && (
                            <motion.div
                                variants={containerVariants()}
                                initial="hidden"
                                animate="show"
                                className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
                            >
                                {secondaryIndices.map((index) => {
                                    const cls = getChangeClass(index.priceChangePercent);
                                    return (
                                        <motion.div
                                            key={index.symbol}
                                            variants={cardVariants}
                                            className="group rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 card-hover transition-all duration-200 hover:border-border-hover"
                                        >
                                            <div className="flex items-center justify-between gap-2">
                                                <div className="min-w-0">
                                                    <h3 className="truncate text-xs font-semibold text-fg">{index.symbol.replace('.IS', '')}</h3>
                                                    <span className="block truncate text-[10px] text-fg-muted">{getIndexLongName(index.symbol)}</span>
                                                </div>
                                                <div className={`shrink-0 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                                    {index.priceChangePercent > 0 ? <ChevronUp className="h-3 w-3" /> : index.priceChangePercent < 0 ? <ChevronDown className="h-3 w-3" /> : null}
                                                    {Math.abs(index.priceChangePercent || 0).toFixed(2)}%
                                                </div>
                                            </div>
                                            <p className="mt-1 font-mono text-sm font-bold text-fg">
                                                {formatStockPrice(index.currentPrice)}
                                            </p>
                                        </motion.div>
                                    );
                                })}
                            </motion.div>
                        )}
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
                        variants={containerVariants()}
                        initial="hidden"
                        animate="show"
                        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                    >
                        {regularStocks.map((stock) => {
                            const cls = getChangeClass(stock.priceChangePercent);
                            return (
                                <motion.div
                                    key={stock.symbol}
                                    variants={cardVariants}
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
                                            ₺{formatStockPrice(stock.currentPrice)}
                                        </p>
                                        <div className={`mt-1 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                            {stock.priceChangePercent > 0 ? (
                                                <TrendingUp className="h-3.5 w-3.5" />
                                            ) : stock.priceChangePercent < 0 ? (
                                                <TrendingDown className="h-3.5 w-3.5" />
                                            ) : null}
                                            {Math.abs(stock.priceChangePercent || 0).toFixed(2)}%
                                            <span className="ml-1 opacity-75">
                                                ({stock.priceChangeAmount > 0 ? '+' : ''}₺{formatStockPrice(stock.priceChangeAmount)})
                                            </span>
                                        </div>
                                    </div>
                                    {}
                                    <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                                        {stock.openPrice != null && (
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="text-fg-muted">Açılış</span>
                                                <span className="font-mono text-fg">₺{formatStockPrice(stock.openPrice)}</span>
                                            </div>
                                        )}
                                        {stock.dayHigh != null && (
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="flex items-center gap-1 text-fg-muted">
                                                    <ChevronUp className="h-3 w-3 text-success" />
                                                    En Yüksek
                                                </span>
                                                <span className="font-mono text-fg">₺{formatStockPrice(stock.dayHigh)}</span>
                                            </div>
                                        )}
                                        {stock.dayLow != null && (
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="flex items-center gap-1 text-fg-muted">
                                                    <ChevronDown className="h-3 w-3 text-danger" />
                                                    En Düşük
                                                </span>
                                                <span className="font-mono text-fg">₺{formatStockPrice(stock.dayLow)}</span>
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
                <EmptyState
                    icon={<BarChart2 className="h-7 w-7 text-fg-subtle" />}
                    message="Henüz hisse senedi verisi yok."
                    hint={isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : 'Admin veri güncellemesini bekleyin.'}
                />
            )}
        </div>
    );
}
export default Stocks;
