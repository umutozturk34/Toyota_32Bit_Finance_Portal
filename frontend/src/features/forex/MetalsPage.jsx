import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Gem,
    CircleDot,
    Star,
    RefreshCw,
    ArrowUpRight,
    ArrowDownRight,
    TrendingUp,
    BarChart2,
    Clock,
} from 'lucide-react';
import { metalService } from '../services/dataService';
import { getChangeClass, changeColors, changeBg, formatPriceUSD } from '../utils/formatters';
import { containerVariants, cardVariants } from '../utils/animations';
import LoadingState from '../components/LoadingState';
import ErrorState from '../components/ErrorState';
import EmptyState from '../components/EmptyState';
const Metals = () => {
    const navigate = useNavigate();
    const [metals, setMetals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    useEffect(() => {
        fetchMetals();
    }, []);
    const fetchMetals = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await metalService.getMetals();
            setMetals(data || []);
        } catch (err) {
            console.error('Error fetching metals:', err);
            setError('Failed to load precious metals data. Please try again later.');
        } finally {
            setLoading(false);
        }
    };
    const getMetalIcon = (symbol) => {
        switch (symbol) {
            case 'PAXG':
            case 'XAUT':
            case 'GOLD':
                return <CircleDot className="h-5 w-5" />;
            case 'KAG':
            case 'SILVER':
                return <Star className="h-5 w-5" />;
            case 'PLATINUM':
                return <Star className="h-5 w-5" />;
            default:
                return <Gem className="h-5 w-5" />;
        }
    };
    const changeIcons = {
        positive: ArrowUpRight,
        negative: ArrowDownRight,
        neutral: TrendingUp,
    };
    if (loading) return <LoadingState message="Kıymetli maden verileri yükleniyor…" />;

    if (error) return <ErrorState message={error} onRetry={fetchMetals} />;
        return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="space-y-6 py-6"
        >
            {}
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <motion.h1
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        className="flex items-center gap-3 text-2xl font-bold tracking-[-0.025em] text-fg"
                    >
                        <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                            <Gem className="h-5 w-5" />
                        </span>
                        Kıymetli Madenler
                    </motion.h1>
                    <motion.p
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.1 }}
                        className="mt-1 text-sm text-fg-muted"
                    >
                        Altın, Gümüş ve diğer değerli metallerin canlı fiyatları
                    </motion.p>
                </div>
                <motion.button
                    initial={{ opacity: 0, x: 20 }}
                    animate={{ opacity: 1, x: 0 }}
                    whileTap={{ scale: 0.97 }}
                    onClick={fetchMetals}
                    className="flex items-center gap-2 rounded-md border border-border-default bg-surface px-5 py-2.5 text-sm text-fg transition-colors duration-150 hover:bg-surface-hover"
                >
                    <RefreshCw className="h-4 w-4" /> Yenile
                </motion.button>
            </div>
            {}
            {metals.length === 0 ? (
                <EmptyState
                    icon={<Gem className="h-8 w-8 text-fg-subtle" />}
                    message="Şu anda kıymetli maden verisi mevcut değil."
                />
            ) : (
                <motion.div
                    variants={containerVariants(0.07)}
                    initial="hidden"
                    animate="show"
                    className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
                >
                    <AnimatePresence>
                        {metals.map((metal) => {
                            const cls = getChangeClass(metal.changePercent);
                            const ChangeIcon = changeIcons[cls];
                            return (
                                <motion.div
                                    key={metal.id}
                                    variants={cardVariants}
                                    layout
                                    className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                >
                                    {}
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-3">
                                            <div className="flex h-9 w-9 items-center justify-center rounded-md bg-surface p-2 text-fg-subtle">
                                                {getMetalIcon(metal.symbol)}
                                            </div>
                                            <div>
                                                <h3 className="text-base font-semibold text-fg">
                                                    {metal.symbol}
                                                </h3>
                                                <span className="block truncate text-xs text-fg-muted">
                                                    {metal.name}
                                                </span>
                                            </div>
                                        </div>
                                        <button
                                            onClick={() =>
                                                navigate(
                                                    `/charts?type=METAL&symbol=${metal.symbol}&range=1M`
                                                )
                                            }
                                            title="Grafiği Görüntüle"
                                            className="flex h-8 w-8 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle transition-colors duration-150 hover:bg-surface hover:text-accent"
                                        >
                                            <BarChart2 className="h-4 w-4" />
                                        </button>
                                    </div>
                                    {}
                                    <div className="mt-4 space-y-2">
                                        <span className="text-2xl font-bold tracking-tight text-fg">
                                            {formatPriceUSD(metal.priceUsd)}
                                        </span>
                                        <div className="flex items-center gap-2">
                                            <span
                                                className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}
                                            >
                                                <ChangeIcon className="h-3 w-3" />
                                                {Math.abs(metal.changePercent).toFixed(2)}%
                                            </span>
                                            <span className={`text-xs ${changeColors[cls]}`}>
                                                ({metal.changeAmount > 0 ? '+' : ''}
                                                {formatPriceUSD(metal.changeAmount)})
                                            </span>
                                        </div>
                                    </div>
                                    {}
                                    <div className="mt-3 flex items-center gap-1.5 text-[11px] text-fg-subtle">
                                        <Clock className="h-3 w-3" />
                                        Son güncelleme:{' '}
                                        {new Date(metal.timestamp).toLocaleString('tr-TR')}
                                    </div>
                                </motion.div>
                            );
                        })}
                    </AnimatePresence>
                </motion.div>
            )}
        </motion.div>
    );
};
export default Metals;