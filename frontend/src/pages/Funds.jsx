import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    LineChart,
    TrendingUp,
    BarChart2,
    Activity,
    Clock,
    Users as UsersIcon,
    Wallet,
    Filter,
} from 'lucide-react';
import { fundService, adminService } from '../services/marketService';
import { getFundDisplayName } from '../constants/funds';
import { useAuth } from '../context/AuthContext';
import { formatPriceTRY, formatCompactNumber } from '../utils/formatters';
import { containerVariants, cardVariants } from '../utils/animations';
import LoadingState from '../components/LoadingState';
import ErrorState from '../components/ErrorState';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';

function Funds() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [funds, setFunds] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({});
    const [typeFilter, setTypeFilter] = useState('ALL');
    const isAdmin = hasRole('ADMIN');

    useEffect(() => {
        fetchFunds();
    }, []);

    const fetchFunds = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await fundService.getAllFunds();
            setFunds(data);
        } catch (err) {
            setError('Fon verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const formatTRY = (val) => {
        if (val == null) return 'N/A';
        return new Intl.NumberFormat('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 6 }).format(val);
    };

    const formatCompact = (val) => {
        if (val == null) return 'N/A';
        if (val >= 1_000_000_000) return `${(val / 1_000_000_000).toFixed(1)}B`;
        if (val >= 1_000_000) return `${(val / 1_000_000).toFixed(1)}M`;
        if (val >= 1_000) return `${(val / 1_000).toFixed(1)}K`;
        return new Intl.NumberFormat('tr-TR').format(val);
    };

    const handleFundSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerFundSnapshot();
            alert(response.message || 'Fon snapshot güncelleme başlatıldı');
            setTimeout(fetchFunds, 5000);
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };
    const handleFundCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerFundCandles();
            alert(response.message || 'Fon candle güncelleme başlatıldı');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };
    const handleFundFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerFundFull();
            alert(response.message || 'Fon tam güncelleme başlatıldı');
            setTimeout(fetchFunds, 10000);
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, full: false }));
        }
    };
    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'Fon snapshot verilerini güncelle', handler: handleFundSnapshotUpdate },
        { key: 'candles', label: 'Candles', title: 'Fon mum verilerini güncelle', handler: handleFundCandlesUpdate },
        { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + candles)', handler: handleFundFullUpdate },
    ];

    const filteredFunds = typeFilter === 'ALL'
        ? funds
        : funds.filter(f => f.fundType === typeFilter);

    const fundTypes = [...new Set(funds.map(f => f.fundType).filter(Boolean))];

    if (loading && funds.length === 0) return <LoadingState message="Fon verileri yükleniyor…" />;
    if (error) return <ErrorState message={error} onRetry={fetchFunds} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<LineChart className="h-5 w-5" />}
                title="Yatırım Fonları"
                onRefresh={fetchFunds}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

            {fundTypes.length > 0 && (
                <div className="flex items-center gap-2">
                    <Filter className="h-4 w-4 text-fg-muted" />
                    <div className="flex gap-1">
                        <button
                            onClick={() => setTypeFilter('ALL')}
                            className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                                typeFilter === 'ALL'
                                    ? 'bg-accent text-white'
                                    : 'bg-bg-elevated text-fg-muted hover:text-fg border border-border-default'
                            }`}
                        >
                            Tümü ({funds.length})
                        </button>
                        {fundTypes.map(type => (
                            <button
                                key={type}
                                onClick={() => setTypeFilter(type)}
                                className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                                    typeFilter === type
                                        ? 'bg-accent text-white'
                                        : 'bg-bg-elevated text-fg-muted hover:text-fg border border-border-default'
                                }`}
                            >
                                {type} ({funds.filter(f => f.fundType === type).length})
                            </button>
                        ))}
                    </div>
                </div>
            )}

            <AnimatePresence>
                {filteredFunds.length > 0 ? (
                    <motion.div
                        variants={containerVariants(0.06)}
                        initial="hidden"
                        animate="show"
                        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                    >
                        {filteredFunds.map((fund) => (
                            <motion.div
                                key={fund.fundCode}
                                variants={cardVariants}
                                className="group rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover overflow-hidden min-w-0"
                            >
                                <div className="flex items-start justify-between gap-2 min-w-0">
                                    <div className="flex items-center gap-3 min-w-0 flex-1">
                                        <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent shrink-0">
                                            <LineChart className="w-4.5 h-4.5" />
                                        </span>
                                        <div className="min-w-0">
                                            <h3 className="truncate text-sm font-semibold text-fg">{fund.fundCode}</h3>
                                            <span className="block truncate text-xs text-fg-muted">{fund.name || getFundDisplayName(fund.fundCode)}</span>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2 shrink-0">
                                        <span className="rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
                                            {fund.fundType || 'FON'}
                                        </span>
                                        <button
                                            onClick={(e) => {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                navigate(`/charts?type=FUND&symbol=${fund.fundCode}&range=1Y`);
                                            }}
                                            title="Grafiği Görüntüle"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                        >
                                            <BarChart2 className="h-3.5 w-3.5 text-fg-subtle group-hover:text-accent transition-colors duration-150" />
                                        </button>
                                    </div>
                                </div>

                                <div className="mt-3 space-y-1">
                                    <span className="block truncate font-mono text-xl font-bold text-fg">
                                        ₺{formatTRY(fund.price)}
                                    </span>
                                    {fund.fundType === 'BYF' && fund.bulletinPrice != null && (
                                        <div className="flex items-center gap-2 text-xs text-fg-muted">
                                            <span className="font-medium">Borsa Fiyatı</span>
                                            <span className="font-mono">₺{formatTRY(fund.bulletinPrice)}</span>
                                        </div>
                                    )}
                                </div>

                                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                                    {fund.fundType === 'YAT' && fund.investorCount != null && (
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="flex items-center gap-1 text-fg-muted">
                                                <UsersIcon className="h-3 w-3" />
                                                Yatırımcı
                                            </span>
                                            <span className="font-mono text-fg">{formatCompact(fund.investorCount)}</span>
                                        </div>
                                    )}
                                    <div className="flex items-center justify-between text-xs">
                                        <span className="flex items-center gap-1 text-fg-muted">
                                            <Wallet className="h-3 w-3" />
                                            Portföy
                                        </span>
                                        <span className="font-mono text-fg">₺{formatCompact(fund.portfolioSize)}</span>
                                    </div>
                                    <div className="flex items-center justify-between text-xs">
                                        <span className="flex items-center gap-1 text-fg-muted">
                                            <Activity className="h-3 w-3" />
                                            Pay Sayısı
                                        </span>
                                        <span className="font-mono text-fg">{formatCompact(fund.shareCount)}</span>
                                    </div>
                                </div>

                                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                                    <Clock className="h-3 w-3" />
                                    {fund.lastUpdated ? new Date(fund.lastUpdated).toLocaleString('tr-TR') : 'N/A'}
                                </div>
                            </motion.div>
                        ))}
                    </motion.div>
                ) : (
                    <EmptyState message="Henüz fon verisi bulunmuyor" />
                )}
            </AnimatePresence>
        </div>
    );
}

export default Funds;
