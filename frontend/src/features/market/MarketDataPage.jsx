import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
    DollarSign,
    ArrowDownUp,
    Gem,
    CircleDot,
    Star,
    RefreshCw,
    TrendingUp,
    TrendingDown,
    AlertCircle,
    Loader2,
    Coins,
} from 'lucide-react';
import { exchangeRateService, metalService } from '../services/dataService';
const container = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.04 } },
};
const card = {
    hidden: { opacity: 0, y: 10 },
    show: { opacity: 1, y: 0, transition: { duration: 0.25, ease: 'easeOut' } },
};
const MarketData = () => {
    const [rates, setRates] = useState([]);
    const [metals, setMetals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [activeTab, setActiveTab] = useState('forex');
    useEffect(() => {
        fetchData();
    }, []);
    const fetchData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [ratesResponse, metalsResponse] = await Promise.all([
                exchangeRateService.getRates(),
                metalService.getLatestPrices().catch(() => ({ success: false, data: [] })),
            ]);
            if (ratesResponse.success && ratesResponse.data) {
                setRates(ratesResponse.data);
            }
            if (metalsResponse.success && metalsResponse.data) {
                setMetals(metalsResponse.data);
            }
        } catch (err) {
            console.error('Error fetching data:', err);
            setError('Failed to load market data. Please try again later.');
        } finally {
            setLoading(false);
        }
    };
    const getCurrencyIcon = (code) => {
        const iconMap = {
            USD: <DollarSign className="h-5 w-5" />,
            EUR: <Coins className="h-5 w-5" />,
            GBP: <DollarSign className="h-5 w-5" />,
            JPY: <DollarSign className="h-5 w-5" />,
            CHF: <DollarSign className="h-5 w-5" />,
            CAD: <DollarSign className="h-5 w-5" />,
            AUD: <DollarSign className="h-5 w-5" />,
            SAR: <DollarSign className="h-5 w-5" />,
            KWD: <DollarSign className="h-5 w-5" />,
            SEK: <DollarSign className="h-5 w-5" />,
            NOK: <DollarSign className="h-5 w-5" />,
            DKK: <DollarSign className="h-5 w-5" />,
        };
        return iconMap[code] || <ArrowDownUp className="h-5 w-5" />;
    };
    const formatRate = (rate) => parseFloat(rate).toFixed(4);
    const formatDate = (dateString) => {
        const date = new Date(dateString);
        return date.toLocaleDateString('tr-TR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
        });
    };
    const getMetalIcon = (symbol) => {
        const icons = {
            PAXG: <CircleDot className="h-5 w-5 text-warning" />,
            XAUT: <CircleDot className="h-5 w-5 text-warning" />,
            KAG: <Star className="h-5 w-5 text-fg-muted" />,
            GOLD: <CircleDot className="h-5 w-5 text-warning" />,
            SILVER: <Star className="h-5 w-5 text-fg-muted" />,
            PLATINUM: <Star className="h-5 w-5 text-accent-bright" />,
        };
        return icons[symbol] || <Gem className="h-5 w-5 text-accent" />;
    };
    const getMetalName = (symbol) => {
        const names = {
            PAXG: 'PAX Gold (Altın)',
            XAUT: 'Tether Gold (Altın)',
            KAG: 'Kinesis Silver (Gümüş)',
            GOLD: 'Altın',
            SILVER: 'Gümüş',
            PLATINUM: 'Platin',
        };
        return names[symbol] || symbol;
    };
        if (loading) {
        return (
            <div className="flex min-h-[60vh] items-center justify-center">
                <div className="flex flex-col items-center gap-3">
                    <Loader2 className="h-8 w-8 animate-spin text-accent" />
                    <span className="text-fg-muted text-sm">Loading market data…</span>
                </div>
            </div>
        );
    }
        if (error) {
        return (
            <div className="flex min-h-[60vh] flex-col items-center justify-center gap-5">
                <div className="flex flex-col items-center gap-3 rounded-lg border border-danger/30 bg-bg-base px-6 py-5">
                    <AlertCircle className="h-7 w-7 text-danger" />
                    <p className="text-fg text-sm">{error}</p>
                </div>
                <button
                    onClick={fetchData}
                    className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-base px-4 py-2 text-sm text-fg transition-colors hover:bg-surface hover:border-border-hover"
                >
                    <RefreshCw className="h-4 w-4" />
                    Retry
                </button>
            </div>
        );
    }
        return (
        <div className="space-y-6 py-6">
            {}
            <motion.div
                initial={{ opacity: 0, y: -12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, ease: 'easeOut' }}
                className="space-y-1"
            >
                <h1 className="group flex items-center gap-3 text-2xl font-bold tracking-[-0.025em] text-fg sm:text-3xl">
                    <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                        <ArrowDownUp className="h-5 w-5" />
                    </span>
                    Market Data
                </h1>
                <p className="text-sm text-fg-muted mt-0.5 ml-12">Exchange rates &amp; precious metals prices</p>
            </motion.div>
            {}
            <div className="flex flex-wrap items-center gap-3">
                <div className="flex rounded-xl border border-border-default bg-bg-elevated p-1">
                    <button
                        onClick={() => setActiveTab('forex')}
                        className={`flex items-center gap-2 rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors ${activeTab === 'forex'
                            ? 'bg-accent/15 text-accent-bright'
                            : 'text-fg-muted hover:text-fg'
                            }`}
                    >
                        <ArrowDownUp className="h-4 w-4" />
                        Döviz Kurları
                    </button>
                    <button
                        onClick={() => setActiveTab('metals')}
                        className={`flex items-center gap-2 rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors ${activeTab === 'metals'
                            ? 'bg-accent/15 text-accent-bright'
                            : 'text-fg-muted hover:text-fg'
                            }`}
                    >
                        <Gem className="h-4 w-4" />
                        Kıymetli Madenler
                    </button>
                </div>
                <button
                    onClick={fetchData}
                    className="ml-auto flex items-center gap-2 rounded-lg border border-border-default bg-bg-base px-3.5 py-1.5 text-sm text-fg-muted transition-colors hover:bg-surface hover:border-border-hover hover:text-fg"
                >
                    <RefreshCw className="h-4 w-4" />
                    Refresh Data
                </button>
            </div>
            {}
            <AnimatePresence mode="wait">
                {activeTab === 'forex' && (
                    <motion.div
                        key="forex"
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -8 }}
                        transition={{ duration: 0.2 }}
                    >
                        {rates.length === 0 ? (
                            <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-border-default bg-bg-base py-14">
                                <ArrowDownUp className="h-7 w-7 text-fg-subtle" />
                                <p className="text-sm text-fg-muted">No exchange rates available at the moment.</p>
                            </div>
                        ) : (
                            <motion.div
                                variants={container}
                                initial="hidden"
                                animate="show"
                                className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                            >
                                {rates.map((rate) => (
                                    <motion.div
                                        key={rate.id}
                                        variants={card}
                                        className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                    >
                                        {}
                                        <div className="flex items-center gap-3">
                                            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-surface text-fg-subtle group-hover:text-accent transition-colors">
                                                {getCurrencyIcon(rate.currencyCode)}
                                            </div>
                                            <div className="min-w-0">
                                                <h3 className="truncate text-sm font-semibold text-fg">{rate.currencyName}</h3>
                                                <span className="text-xs text-fg-muted">{rate.currencyCode}/TRY</span>
                                            </div>
                                        </div>
                                        {}
                                        <div className="mt-3 space-y-1.5">
                                            <div className="flex items-center justify-between rounded-md bg-success/5 px-3 py-1.5">
                                                <span className="text-xs text-fg-muted">Buying</span>
                                                <span className="font-mono text-sm font-semibold text-success">
                                                    ₺{formatRate(rate.buyingRate)}
                                                </span>
                                            </div>
                                            <div className="flex items-center justify-between rounded-md bg-danger/5 px-3 py-1.5">
                                                <span className="text-xs text-fg-muted">Selling</span>
                                                <span className="font-mono text-sm font-semibold text-danger">
                                                    ₺{formatRate(rate.sellingRate)}
                                                </span>
                                            </div>
                                        </div>
                                        {}
                                        <p className="mt-2.5 text-right text-[11px] text-fg-subtle">
                                            {formatDate(rate.rateDate)}
                                        </p>
                                    </motion.div>
                                ))}
                            </motion.div>
                        )}
                    </motion.div>
                )}
                {}
                {activeTab === 'metals' && (
                    <motion.div
                        key="metals"
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -8 }}
                        transition={{ duration: 0.2 }}
                    >
                        {metals.length === 0 ? (
                            <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-border-default bg-bg-base py-14">
                                <Gem className="h-7 w-7 text-fg-subtle" />
                                <p className="text-sm text-fg-muted">Kıymetli maden verileri yükleniyor…</p>
                                <p className="text-xs text-fg-subtle">Veriler her 15 dakikada bir güncellenir.</p>
                            </div>
                        ) : (
                            <motion.div
                                variants={container}
                                initial="hidden"
                                animate="show"
                                className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
                            >
                                {metals.map((metal) => (
                                    <motion.div
                                        key={metal.id}
                                        variants={card}
                                        className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                    >
                                        {}
                                        <div className="flex items-center gap-3">
                                            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-surface">
                                                {getMetalIcon(metal.symbol)}
                                            </div>
                                            <div className="min-w-0">
                                                <h3 className="truncate text-sm font-semibold text-fg">{getMetalName(metal.symbol)}</h3>
                                                <span className="text-xs text-fg-muted">{metal.symbol}</span>
                                            </div>
                                        </div>
                                        {}
                                        <p className="mt-3 font-mono text-xl font-bold text-fg">
                                            ${parseFloat(metal.priceUsd).toLocaleString('en-US', {
                                                minimumFractionDigits: 2,
                                                maximumFractionDigits: 2,
                                            })}
                                        </p>
                                        {}
                                        <div
                                            className={`mt-1.5 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${metal.changePercent >= 0
                                                ? 'bg-success/10 text-success'
                                                : 'bg-danger/10 text-danger'
                                                }`}
                                        >
                                            {metal.changePercent >= 0 ? (
                                                <TrendingUp className="h-3.5 w-3.5" />
                                            ) : (
                                                <TrendingDown className="h-3.5 w-3.5" />
                                            )}
                                            {metal.changePercent >= 0 ? '+' : ''}
                                            {parseFloat(metal.changePercent).toFixed(2)}%
                                        </div>
                                        {}
                                        <p className="mt-2.5 text-right text-[11px] text-fg-subtle">
                                            {formatDate(metal.timestamp)}
                                        </p>
                                    </motion.div>
                                ))}
                            </motion.div>
                        )}
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
};
export default MarketData;
