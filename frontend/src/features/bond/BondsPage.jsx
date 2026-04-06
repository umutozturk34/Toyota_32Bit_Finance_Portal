import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Chart from 'react-apexcharts';
import {
    Landmark,
    Clock,
    Filter,
    Calendar,
    Percent,
    TrendingUp,
    Building2,
    ChevronDown,
    ChevronUp,
    BarChart3,
} from 'lucide-react';
import { bondService } from './bondService';
import { adminService } from '../admin/adminService';
import { useAuth } from '../auth/AuthContext';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import EmptyState from '../../shared/components/EmptyState';
import PageHeader from '../../shared/components/PageHeader';
import { toast } from '../../shared/components/Toast';

const BOND_TYPE_LABELS = {
    DISCOUNTED: 'İskontolu',
    FIXED_COUPON: 'Sabit Kuponlu',
    FLOATING_TLREF: 'Değişken - TLREF',
    FLOATING_CPI: 'Değişken - TÜFE',
    FLOATING_AUCTION: 'Değişken - İhale',
    SUKUK_FIXED: 'Sukuk - Sabit',
    SUKUK_CPI: 'Sukuk - TÜFE',
};

const BOND_TYPE_COLORS = {
    DISCOUNTED: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    FIXED_COUPON: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    FLOATING_TLREF: 'bg-violet-500/10 text-violet-400 border-violet-500/20',
    FLOATING_CPI: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
    FLOATING_AUCTION: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    SUKUK_FIXED: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
    SUKUK_CPI: 'bg-pink-500/10 text-pink-400 border-pink-500/20',
};

const CHART_LINE_COLORS = {
    DISCOUNTED: '#3b82f6',
    FIXED_COUPON: '#10b981',
    FLOATING_TLREF: '#8b5cf6',
    FLOATING_CPI: '#f43f5e',
    FLOATING_AUCTION: '#f59e0b',
    SUKUK_FIXED: '#06b6d4',
    SUKUK_CPI: '#ec4899',
};

function RateHistoryChart({ isinCode, bondType }) {
    const [rateData, setRateData] = useState(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        fetchRateHistory();
    }, [isinCode]);

    const fetchRateHistory = async () => {
        setLoading(true);
        try {
            const data = await bondService.getRateHistory(isinCode);
            setRateData(data);
        } catch {
            setRateData([]);
        } finally {
            setLoading(false);
        }
    };

    if (loading) return <div className="h-48 flex items-center justify-center text-fg-muted text-xs">Grafik yükleniyor…</div>;
    if (!rateData || rateData.length === 0) return <div className="h-48 flex items-center justify-center text-fg-muted text-xs">Rate verisi yok</div>;

    const lineColor = CHART_LINE_COLORS[bondType] || '#8b5cf6';

    const options = {
        chart: {
            type: 'line',
            height: 200,
            background: 'transparent',
            toolbar: { show: false },
            zoom: { enabled: true },
            fontFamily: 'inherit',
        },
        stroke: { curve: 'smooth', width: 2 },
        colors: [lineColor],
        xaxis: {
            type: 'datetime',
            categories: rateData.map(d => d.date),
            labels: {
                style: { colors: 'var(--fg-muted)', fontSize: '10px' },
                datetimeFormatter: { month: 'MMM yy' },
            },
            axisBorder: { show: false },
            axisTicks: { show: false },
        },
        yaxis: {
            labels: {
                style: { colors: 'var(--fg-muted)', fontSize: '10px' },
                formatter: (val) => `%${val.toFixed(2)}`,
            },
        },
        grid: {
            borderColor: 'var(--border-default)',
            strokeDashArray: 3,
            xaxis: { lines: { show: false } },
        },
        tooltip: {
            theme: 'dark',
            x: { format: 'dd MMM yyyy' },
            y: { formatter: (val) => `%${val.toFixed(4)}` },
        },
        dataLabels: { enabled: false },
    };

    const series = [{
        name: 'Kupon Oranı',
        data: rateData.map(d => Number(d.rate)),
    }];

    return <Chart options={options} series={series} type="line" height={200} />;
}

function BondsPage() {
    const { hasRole } = useAuth();
    const [bonds, setBonds] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({});
    const [typeFilter, setTypeFilter] = useState('ALL');
    const [expandedBond, setExpandedBond] = useState(null);
    const isAdmin = hasRole('ADMIN');

    useEffect(() => {
        fetchBonds();
    }, []);

    const fetchBonds = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await bondService.getAllBonds();
            setBonds(data);
        } catch (err) {
            setError('Tahvil verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const formatRate = (val) => {
        if (val == null) return 'N/A';
        return `%${Number(val).toFixed(2)}`;
    };

    const formatPrice = (val) => {
        if (val == null) return 'N/A';
        return new Intl.NumberFormat('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(val);
    };

    const formatDate = (val) => {
        if (!val) return 'N/A';
        return new Date(val).toLocaleDateString('tr-TR', { timeZone: 'Europe/Istanbul' });
    };

    const daysUntil = (dateStr) => {
        if (!dateStr) return null;
        const target = new Date(dateStr);
        const now = new Date();
        return Math.ceil((target - now) / (1000 * 60 * 60 * 24));
    };

    const isFloatingType = (bondType) => {
        return ['FLOATING_TLREF', 'FLOATING_CPI', 'FLOATING_AUCTION'].includes(bondType);
    };

    const toggleChart = (seriesCode) => {
        setExpandedBond(prev => prev === seriesCode ? null : seriesCode);
    };

    const handleBondUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerBondUpdate();
            toast.success('Güncelleme Başlatıldı', response.message || 'Tahvil güncelleme başlatıldı');
            setTimeout(fetchBonds, 10000);
        } catch (err) {
            toast.error('Güncelleme Hatası', err.response?.data?.message || err.message);
        } finally {
            setUpdating(prev => ({ ...prev, full: false }));
        }
    };

    const adminActions = [
        { key: 'full', label: 'Güncelle', title: 'Tahvil verilerini güncelle (snapshot + faiz geçmişi)', handler: handleBondUpdate },
    ];

    const bondTypes = [...new Set(bonds.map(b => b.bondType).filter(Boolean))];
    const filteredBonds = typeFilter === 'ALL' ? bonds : bonds.filter(b => b.bondType === typeFilter);

    if (loading && bonds.length === 0) return <LoadingState message="Tahvil verileri yükleniyor…" />;
    if (error) return <ErrorState message={error} onRetry={fetchBonds} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Landmark className="h-5 w-5" />}
                title="Tahvil & Bono"
                onRefresh={fetchBonds}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

            {bondTypes.length > 0 && (
                <div className="flex items-center gap-2 flex-wrap">
                    <Filter className="h-4 w-4 text-fg-muted" />
                    <div className="flex gap-1 flex-wrap">
                        <button
                            onClick={() => setTypeFilter('ALL')}
                            className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                                typeFilter === 'ALL'
                                    ? 'bg-accent text-white'
                                    : 'bg-bg-elevated text-fg-muted hover:text-fg border border-border-default'
                            }`}
                        >
                            Tümü ({bonds.length})
                        </button>
                        {bondTypes.map(type => (
                            <button
                                key={type}
                                onClick={() => setTypeFilter(type)}
                                className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                                    typeFilter === type
                                        ? 'bg-accent text-white'
                                        : 'bg-bg-elevated text-fg-muted hover:text-fg border border-border-default'
                                }`}
                            >
                                {BOND_TYPE_LABELS[type] || type} ({bonds.filter(b => b.bondType === type).length})
                            </button>
                        ))}
                    </div>
                </div>
            )}

            <AnimatePresence>
                {filteredBonds.length > 0 ? (
                    <motion.div
                        variants={containerVariants(0.06)}
                        initial="hidden"
                        animate="show"
                        className="flex flex-col gap-4"
                    >
                        {filteredBonds.map((bond) => {
                            const maturityDays = daysUntil(bond.maturityEnd);
                            const couponDays = daysUntil(bond.nextCouponDate);
                            const typeColor = BOND_TYPE_COLORS[bond.bondType] || 'bg-accent/10 text-accent border-accent/20';
                            const isExpanded = expandedBond === bond.seriesCode;

                            return (
                                <motion.div
                                    key={bond.seriesCode}
                                    variants={cardVariants}
                                    layout
                                    className="rounded-2xl border border-border-default bg-bg-elevated card-hover transition-all duration-200 hover:border-border-hover overflow-hidden"
                                >
                                    <div className="p-6">
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="flex items-center gap-4 min-w-0 flex-1">
                                                <span className="flex items-center justify-center w-12 h-12 rounded-xl bg-accent/10 text-accent shrink-0">
                                                    <Landmark className="w-6 h-6" />
                                                </span>
                                                <div className="min-w-0">
                                                    <h3 className="text-lg font-bold text-fg">{bond.isinCode}</h3>
                                                    <span className="block text-sm text-fg-muted">{bond.seriesCode}</span>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-3 shrink-0">
                                                <span className={`rounded-lg border px-3 py-1 text-xs font-semibold tracking-wider ${typeColor}`}>
                                                    {BOND_TYPE_LABELS[bond.bondType] || bond.bondType}
                                                </span>
                                                <span className="font-mono text-2xl font-bold text-fg">
                                                    {formatPrice(bond.baseIndex)}
                                                </span>
                                            </div>
                                        </div>

                                        <div className="mt-5 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4">
                                            <div className="space-y-1">
                                                <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                                                    <Building2 className="h-3.5 w-3.5" /> İhraççı
                                                </span>
                                                <span className="block text-sm font-medium text-fg">{bond.issuer || 'HAZİNE'}</span>
                                            </div>
                                            <div className="space-y-1">
                                                <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                                                    <Percent className="h-3.5 w-3.5" /> Kupon Oranı
                                                </span>
                                                <span className="block text-sm font-mono text-fg">{formatRate(bond.couponRate)}</span>
                                            </div>
                                            <div className="space-y-1">
                                                <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                                                    <TrendingUp className="h-3.5 w-3.5" /> Basit Getiri
                                                </span>
                                                <span className="block text-sm font-mono text-fg">
                                                    {isFloatingType(bond.bondType)
                                                        ? <span className="text-warning font-medium">Değişken</span>
                                                        : formatRate(bond.simpleYield)
                                                    }
                                                </span>
                                            </div>
                                            <div className="space-y-1">
                                                <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                                                    <Calendar className="h-3.5 w-3.5" /> Başlangıç
                                                </span>
                                                <span className="block text-sm font-mono text-fg">{formatDate(bond.maturityStart)}</span>
                                            </div>
                                            <div className="space-y-1">
                                                <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                                                    <Calendar className="h-3.5 w-3.5" /> Vade Sonu
                                                </span>
                                                <span className="block text-sm font-mono text-fg">
                                                    {formatDate(bond.maturityEnd)}
                                                    {maturityDays != null && (
                                                        <span className="ml-1.5 text-fg-subtle">({maturityDays}g)</span>
                                                    )}
                                                </span>
                                            </div>
                                            {bond.nextCouponDate && (
                                                <div className="space-y-1">
                                                    <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                                                        <Calendar className="h-3.5 w-3.5" /> Kupon Tarihi
                                                    </span>
                                                    <span className="block text-sm font-mono text-fg">
                                                        {formatDate(bond.nextCouponDate)}
                                                        {couponDays != null && (
                                                            <span className="ml-1.5 text-fg-subtle">({couponDays}g)</span>
                                                        )}
                                                    </span>
                                                </div>
                                            )}
                                        </div>

                                        <div className="mt-4 flex items-center justify-between">
                                            <div className="flex items-center gap-1.5 text-[11px] text-fg-subtle">
                                                <Clock className="h-3 w-3" />
                                                {bond.lastUpdated ? new Date(bond.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                                            </div>
                                            {bond.couponRate != null && Number(bond.couponRate) > 0 && (
                                                <button
                                                    onClick={() => toggleChart(bond.seriesCode)}
                                                    className="flex items-center gap-1.5 text-xs text-fg-muted hover:text-accent transition-colors"
                                                >
                                                    <BarChart3 className="h-3.5 w-3.5" />
                                                    Faiz Değişimi
                                                    {isExpanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                                                </button>
                                            )}
                                        </div>
                                    </div>

                                    <AnimatePresence>
                                        {isExpanded && (
                                            <motion.div
                                                initial={{ height: 0, opacity: 0 }}
                                                animate={{ height: 'auto', opacity: 1 }}
                                                exit={{ height: 0, opacity: 0 }}
                                                transition={{ duration: 0.25 }}
                                                className="overflow-hidden border-t border-border-default"
                                            >
                                                <div className="p-4">
                                                    <RateHistoryChart isinCode={bond.isinCode} bondType={bond.bondType} />
                                                </div>
                                            </motion.div>
                                        )}
                                    </AnimatePresence>
                                </motion.div>
                            );
                        })}
                    </motion.div>
                ) : (
                    <EmptyState message="Henüz tahvil verisi bulunmuyor" />
                )}
            </AnimatePresence>
        </div>
    );
}

export default BondsPage;
