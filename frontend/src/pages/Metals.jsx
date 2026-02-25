import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Gem,
    CircleDot,
    Star,
    TrendingUp,
    TrendingDown,
    RefreshCw,
    Loader2,
    AlertTriangle,
    Clock,
    ArrowUpRight,
    ArrowDownRight,
    BarChart2,
} from 'lucide-react';
import { metalService } from '../services/dataService';
const container = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.07 } },
};
const card = {
    hidden: { opacity: 0, y: 24 },
    show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};
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
            const response = await metalService.getLatestPrices();
            if (response.success && response.data) {
                setMetals(response.data || []);
            } else {
                setError('Failed to load precious metals data');
            }
        } catch (err) {
            console.error('Error fetching metals:', err);
            setError('Failed to load precious metals data. Please try again later.');
        } finally {
            setLoading(false);
        }
    };
    const formatPrice = (price) =>
        new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(price);
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
    const changeStyles = (change) => {
        if (change > 0)
            return { text: 'text-success', bg: 'bg-success/10', Icon: ArrowUpRight };
        if (change < 0)
            return { text: 'text-danger', bg: 'bg-danger/10', Icon: ArrowDownRight };
        return { text: 'text-fg-muted', bg: 'bg-fg-muted/10', Icon: TrendingUp };
    };
        if (loading) {
        return (
            <div className="flex min-h-[60vh] items-center justify-center">
                <motion.div
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="flex flex-col items-center gap-4"
                >
                    <Loader2 className="h-8 w-8 animate-spin text-accent" />
                    <span className="text-sm text-fg-muted tracking-wide">
                        Kıymetli maden verileri yükleniyor…
                    </span>
                </motion.div>
            </div>
        );
    }
        if (error) {
        return (
            <div className="flex min-h-[60vh] items-center justify-center">
                <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex max-w-md flex-col items-center gap-4 rounded-lg border border-border-default bg-bg-base p-8 text-center"
                >
                    <AlertTriangle className="h-8 w-8 text-danger" />
                    <p className="text-sm text-fg-muted">{error}</p>
                    <button
                        onClick={fetchMetals}
                        className="flex items-center gap-2 rounded-md border border-border-default bg-surface px-5 py-2.5 text-sm text-fg transition-colors duration-150 hover:bg-surface-hover"
                    >
                        <RefreshCw className="h-4 w-4" /> Tekrar Dene
                    </button>
                </motion.div>
            </div>
        );
    }
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
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="flex flex-col items-center gap-3 rounded-lg border border-border-default bg-bg-base p-12 text-center"
                >
                    <Gem className="h-8 w-8 text-fg-subtle" />
                    <p className="text-sm text-fg-muted">
                        Şu anda kıymetli maden verisi mevcut değil.
                    </p>
                </motion.div>
            ) : (
                <motion.div
                    variants={container}
                    initial="hidden"
                    animate="show"
                    className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
                >
                    <AnimatePresence>
                        {metals.map((metal) => {
                            const cs = changeStyles(metal.changePercent);
                            const ChangeIcon = cs.Icon;
                            return (
                                <motion.div
                                    key={metal.id}
                                    variants={card}
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
                                            {formatPrice(metal.priceUsd)}
                                        </span>
                                        <div className="flex items-center gap-2">
                                            <span
                                                className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-medium ${cs.bg} ${cs.text}`}
                                            >
                                                <ChangeIcon className="h-3 w-3" />
                                                {Math.abs(metal.changePercent).toFixed(2)}%
                                            </span>
                                            <span className={`text-xs ${cs.text}`}>
                                                ({metal.changeAmount > 0 ? '+' : ''}
                                                {formatPrice(metal.changeAmount)})
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