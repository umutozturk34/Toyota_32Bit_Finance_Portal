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
    ShoppingCart,
} from 'lucide-react';
import { cryptoService } from './cryptoService';
import { adminService } from '../admin/adminService';
import { useAuth } from '../auth/AuthContext';
import { getChangeClass, changeColors, changeBg, formatPriceUSD, formatPriceTRY, formatCompactNumber } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import EmptyState from '../../shared/components/EmptyState';
import PageHeader from '../../shared/components/PageHeader';
import BuyModal from '../../shared/components/BuyModal';
import { toast } from '../../shared/components/Toast';

export default function CryptoPage() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [buyTarget, setBuyTarget] = useState(null);
    const [cryptos, setCryptos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
    });
    const isAdmin = hasRole('ADMIN');

    useEffect(() => {
        fetchCryptos();
    }, []);

    const fetchCryptos = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await cryptoService.getAll();
            setCryptos(data);
        } catch (err) {
            setError('Kripto para verileri yüklenirken hata oluştu');
        } finally {
            setLoading(false);
        }
    };

    const handleTrigger = async (action, triggerFn) => {
        setUpdating(prev => ({ ...prev, [action]: true }));
        try {
            const response = await triggerFn();
            toast.success('Güncelleme Başlatıldı', response.message || 'Güncelleme başlatıldı');
            if (action !== 'candles') setTimeout(fetchCryptos, 5000);
        } catch (err) {
            toast.error('Güncelleme Hatası', err.response?.data?.message || err.message);
        } finally {
            setUpdating(prev => ({ ...prev, [action]: false }));
        }
    };

    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'Kripto snapshot verilerini güncelle', handler: () => handleTrigger('snapshot', adminService.triggerCryptoSnapshot) },
        { key: 'candles', label: 'Candles', title: 'Kripto mum verilerini güncelle', handler: () => handleTrigger('candles', adminService.triggerCryptoCandles) },
        { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + candles)', handler: () => handleTrigger('full', adminService.triggerCryptoFull) },
    ];

    if (loading && cryptos.length === 0) return <LoadingState message="Kripto verileri yükleniyor…" />;
    if (error) return <ErrorState message={error} onRetry={fetchCryptos} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Bitcoin className="h-5 w-5" />}
                title="Kripto Paralar"
                onRefresh={fetchCryptos}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

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
                                    onClick={() => navigate(`/crypto/${crypto.id}`)}
                                    className="group cursor-pointer rounded-2xl border border-border-default bg-bg-elevated p-5 card-hover transition-all duration-200 hover:border-border-hover overflow-hidden relative"
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex items-center gap-3">
                                            {crypto.image ? (
                                                <img src={crypto.image} alt={crypto.symbol} className="w-8 h-8 rounded-full" />
                                            ) : (
                                                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-warning/10 text-xs font-bold text-warning">
                                                    {crypto.symbol?.slice(0, 2)}
                                                </span>
                                            )}
                                            <div className="min-w-0">
                                                <h3 className="truncate text-sm font-semibold text-fg">{crypto.symbol}</h3>
                                                <span className="block truncate text-xs text-fg-muted">{crypto.name}</span>
                                            </div>
                                        </div>
                                        <button
                                            onClick={(e) => {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                setBuyTarget({ assetCode: crypto.id, assetName: `${crypto.symbol} - ${crypto.name}`, price: crypto.currentPriceTry });
                                            }}
                                            title="Satın Al"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                        >
                                            <ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />
                                        </button>
                                    </div>

                                    <div className="mt-3 space-y-1">
                                        <span className="font-mono text-xl font-bold text-fg">
                                            {formatPriceUSD(crypto.currentPrice)}
                                        </span>
                                        <div className="flex items-center gap-2 text-xs text-fg-muted">
                                            <span className="font-medium">TRY</span>
                                            <span className="font-mono">{formatPriceTRY(crypto.currentPriceTry)}</span>
                                        </div>
                                    </div>

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

                                    <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                                        <Clock className="h-3 w-3" />
                                        {crypto.lastUpdated ? new Date(crypto.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                                    </div>
                                </motion.div>
                            );
                        })}
                    </motion.div>
                )}
            </AnimatePresence>

            {cryptos.length === 0 && (
                <EmptyState
                    icon={<Bitcoin className="h-7 w-7 text-fg-subtle" />}
                    message="Henüz kripto para verisi yok."
                    hint={isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : 'Admin veri güncellemesini bekleyin.'}
                />
            )}

            {buyTarget && (
                <BuyModal
                    assetType="CRYPTO"
                    assetCode={buyTarget.assetCode}
                    assetName={buyTarget.assetName}
                    currentPrice={buyTarget.price}
                    onClose={() => setBuyTarget(null)}
                    onComplete={() => setBuyTarget(null)}
                />
            )}
        </div>
    );
}
