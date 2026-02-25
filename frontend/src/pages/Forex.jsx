import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
    DollarSign,
    TrendingUp,
    TrendingDown,
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
    Coins,
} from 'lucide-react';
import { forexService, adminService } from '../services/marketService';
import { getForexPairs, getForexDisplayName, getForexFlag, getBaseCurrency } from '../constants/forex';
import { useAuth } from '../context/AuthContext';
const container = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.05 } },
};
const card = {
    hidden: { opacity: 0, y: 24 },
    show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};
function Forex() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [forexData, setForexData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
        tcmb: false,
    });
    const isAdmin = hasRole('ADMIN');
    console.log('[Forex] isAdmin:', isAdmin);
    useEffect(() => {
        console.log('[Forex] useEffect - fetching forex data');
        fetchForexData();
    }, []);
    const fetchForexData = async () => {
        console.log('[Forex] fetchForexData() called');
        setLoading(true);
        setError(null);
        try {
            const pairs = getForexPairs();
            console.log('[Forex] Fetching forex for pairs:', pairs);
            const data = await forexService.getMultipleForex(pairs);
            console.log('[Forex] fetchForexData() success, data:', data);
            setForexData(data || []);
        } catch (err) {
            console.error('[Forex] fetchForexData() error:', err);
            setError('Döviz kuru verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };
    const handleForexSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerForexSnapshot();
            alert(response.message || 'TCMB + Yahoo snapshot güncelleme başlatıldı (~1 dakika, 21 forex × 2sn)');
            setTimeout(fetchForexData, 5000);
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };
    const handleForexCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerForexCandles();
            alert(response.message || 'Yahoo Finance candles güncelleme başlatıldı (~10 dakika, 20 forex × 5y OHLC)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };
    const handleForexFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerForexFull();
            alert(response.message || 'Yahoo Finance FULL güncelleme başlatıldı (~12 dakika, snapshot + 5y candles)');
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
            minimumFractionDigits: 4,
            maximumFractionDigits: 4
        }).format(price);
    };
    const formatChange = (change) => {
        if (change === null || change === undefined) return 'N/A';
        const prefix = change > 0 ? '+' : '';
        return prefix + new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 4,
            maximumFractionDigits: 4
        }).format(change);
    };
    const formatPercent = (percent) => {
        if (percent === null || percent === undefined) return 'N/A';
        const prefix = percent > 0 ? '+' : '';
        return prefix + percent.toFixed(2) + '%';
    };
    const handleCardClick = (currencyCode) => {
        navigate(`/charts?type=FOREX&symbol=${currencyCode}&range=3M`);
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
                    className="flex flex-col items-center gap-4"
                >
                    <Loader2 className="h-10 w-10 animate-spin text-accent" />
                    <span className="text-fg-muted text-sm tracking-wide">Döviz kurları yükleniyor…</span>
                </motion.div>
            </div>
        );
    }
        if (error) {
        return (
            <div className="flex min-h-[60vh] flex-col items-center justify-center gap-6">
                <motion.div
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col items-center gap-3 rounded-lg border border-danger/30 bg-danger/5 px-8 py-6"
                >
                    <AlertTriangle className="h-8 w-8 text-danger" />
                    <p className="text-fg text-sm">{error}</p>
                </motion.div>
                <button
                    onClick={fetchForexData}
                    className="flex items-center gap-2 rounded-md border border-border-default bg-surface px-5 py-2.5 text-sm text-fg transition-colors duration-150 hover:bg-surface-hover"
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
                className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"
            >
                <div className="space-y-1">
                    <h1 className="flex items-center gap-3 text-2xl font-bold tracking-[-0.025em] text-fg sm:text-3xl">
                        <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                            <Coins className="h-5 w-5" />
                        </span>
                        Döviz Kurları
                    </h1>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    <button
                        onClick={fetchForexData}
                        disabled={loading}
                        className="flex items-center gap-2 rounded-md border border-border-default bg-surface px-4 py-2 text-sm text-fg-muted transition-colors duration-150 hover:bg-surface-hover hover:text-fg disabled:opacity-50"
                    >
                        <RefreshCw className="h-4 w-4" />
                        Yenile
                    </button>
                    {isAdmin && (
                        <>
                            <button
                                onClick={handleForexSnapshotUpdate}
                                disabled={updating.snapshot || loading}
                                title="TCMB + Yahoo snapshot güncelle (~1 dakika, 21 forex × 2sn)"
                                className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                            >
                                {updating.snapshot ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                                Snapshot
                            </button>
                            <button
                                onClick={handleForexCandlesUpdate}
                                disabled={updating.candles || loading}
                                title="Yahoo Finance candles güncelle (~10 dakika, 20 forex × 5y OHLC)"
                                className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                            >
                                {updating.candles ? <Loader2 className="h-4 w-4 animate-spin" /> : <LineChart className="h-4 w-4" />}
                                Candles (5y)
                            </button>
                            <button
                                onClick={handleForexFullUpdate}
                                disabled={updating.full || loading}
                                title="Yahoo Finance FULL update (~12 dakika, snapshot + 5y candles)"
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
            {forexData.length > 0 && (
                <motion.section
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: 0.1 }}
                    className="space-y-4"
                >
                    <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
                        <DollarSign className="h-5 w-5 text-fg-subtle" />
                        Döviz Çiftleri
                        <span className="ml-1 text-sm font-normal text-fg-muted">({forexData.length} çift)</span>
                    </h2>
                    <motion.div
                        variants={container}
                        initial="hidden"
                        animate="show"
                        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                    >
                        {[...forexData].sort((a, b) => {
                            const timeA = a.yahooUpdatedAt ? new Date(a.yahooUpdatedAt).getTime() : 0;
                            const timeB = b.yahooUpdatedAt ? new Date(b.yahooUpdatedAt).getTime() : 0;
                            return timeA - timeB;
                        }).map((forex) => {
                            const cls = getChangeClass(forex.change24h);
                            return (
                                <motion.div
                                    key={forex.currencyCode}
                                    variants={card}
                                    onClick={() => handleCardClick(forex.currencyCode)}
                                    className="group cursor-pointer rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                >
                                    {}
                                    <div className="flex items-start justify-between">
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2">
                                                <span className="text-lg">{getForexFlag(forex.currencyCode)}</span>
                                                <h3 className="truncate text-sm font-semibold text-fg">
                                                    {getBaseCurrency(forex.currencyCode)} / TRY
                                                </h3>
                                            </div>
                                            <span className="mt-0.5 block truncate text-xs text-fg-muted">
                                                {getForexDisplayName(forex.currencyCode)}
                                            </span>
                                        </div>
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handleCardClick(forex.currencyCode);
                                            }}
                                            title="Grafiği Görüntüle"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle transition-colors duration-150 hover:bg-surface hover:text-accent"
                                        >
                                            <BarChart2 className="h-3.5 w-3.5" />
                                        </button>
                                    </div>
                                    {}
                                    {isAdmin && forex.updatedAt && (
                                        <div className="mt-2 flex items-center justify-between rounded-md bg-surface px-2.5 py-1.5 text-[10px] text-fg-subtle">
                                            <span className="flex items-center gap-1">
                                                <BarChart2 className="h-2.5 w-2.5" />
                                                Yahoo: {forex.yahooUpdatedAt ?
                                                    new Date(forex.yahooUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                                    'N/A'
                                                }
                                            </span>
                                            <span className="flex items-center gap-1">
                                                <Activity className="h-2.5 w-2.5" />
                                                TCMB: {forex.tcmbUpdatedAt ?
                                                    new Date(forex.tcmbUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                                    'N/A'
                                                }
                                            </span>
                                        </div>
                                    )}
                                    {}
                                    <div className="mt-3 space-y-1">
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs text-fg-muted">Alış:</span>
                                            <span className="font-mono text-xl font-bold text-fg">₺ {formatPrice(forex.currentPrice)}</span>
                                        </div>
                                        {forex.sellingPrice && (
                                            <div className="flex items-center justify-between">
                                                <span className="text-xs text-fg-muted">Satış:</span>
                                                <span className="font-mono text-base font-semibold text-fg-muted">₺ {formatPrice(forex.sellingPrice)}</span>
                                            </div>
                                        )}
                                    </div>
                                    {}
                                    {(forex.change24h !== null && forex.change24h !== undefined &&
                                        forex.changePercent24h !== null && forex.changePercent24h !== undefined) && (
                                            <div className={`mt-2 inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                                {forex.change24h > 0 ? (
                                                    <ArrowUpRight className="h-3.5 w-3.5" />
                                                ) : forex.change24h < 0 ? (
                                                    <ArrowDownRight className="h-3.5 w-3.5" />
                                                ) : null}
                                                <span>{formatChange(forex.change24h)} TRY</span>
                                                <span className="opacity-75">({formatPercent(forex.changePercent24h)})</span>
                                            </div>
                                        )}
                                    {}
                                    {(forex.forexBuying || forex.forexSelling) && (
                                        <div className="mt-3 space-y-1.5 border-t border-border-default pt-3">
                                            <h4 className="flex items-center gap-1.5 text-xs font-medium text-fg-muted">
                                                <Activity className="h-3 w-3 text-fg-subtle" />
                                                TCMB Kurları
                                            </h4>
                                            {forex.forexBuying && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Döviz Alış:</span>
                                                    <span className="font-mono text-fg">₺ {formatPrice(forex.forexBuying)}</span>
                                                </div>
                                            )}
                                            {forex.forexSelling && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Döviz Satış:</span>
                                                    <span className="font-mono text-fg">₺ {formatPrice(forex.forexSelling)}</span>
                                                </div>
                                            )}
                                            {forex.banknoteBuying && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Efektif Alış:</span>
                                                    <span className="font-mono text-fg">₺ {formatPrice(forex.banknoteBuying)}</span>
                                                </div>
                                            )}
                                            {forex.banknoteSelling && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Efektif Satış:</span>
                                                    <span className="font-mono text-fg">₺ {formatPrice(forex.banknoteSelling)}</span>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    {}
                                    {!isAdmin && forex.updatedAt && (
                                        <div className="mt-3 flex items-center gap-1 text-[11px] text-fg-subtle">
                                            <Clock className="h-3 w-3" />
                                            Son Güncelleme: {(() => {
                                                const tcmbTime = forex.tcmbUpdatedAt ? new Date(forex.tcmbUpdatedAt) : null;
                                                const yahooTime = forex.yahooUpdatedAt ? new Date(forex.yahooUpdatedAt) : null;
                                                const lastUpdate = tcmbTime && yahooTime ?
                                                    (tcmbTime > yahooTime ? tcmbTime : yahooTime) :
                                                    (tcmbTime || yahooTime || new Date(forex.updatedAt));
                                                return lastUpdate.toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
                                            })()}
                                        </div>
                                    )}
                                </motion.div>
                            );
                        })}
                    </motion.div>
                </motion.section>
            )}
            {}
            {forexData.length === 0 && (
                <motion.div
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col items-center justify-center gap-3 rounded-lg border border-border-default bg-bg-base py-16"
                >
                    <Coins className="h-8 w-8 text-fg-subtle" />
                    <p className="text-sm text-fg-muted">
                        Henüz döviz kuru verisi bulunmuyor.
                    </p>
                    <p className="text-xs text-fg-subtle">
                        {isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : 'Admin veri güncellemesini bekleyin.'}
                    </p>
                </motion.div>
            )}
        </div>
    );
}
export default Forex;
