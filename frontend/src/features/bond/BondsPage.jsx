import { useState } from 'react';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import useSessionState from "../../shared/hooks/useSessionState";
import { AnimatePresence } from 'framer-motion';
import ReactECharts from 'echarts-for-react';
import {
    Landmark,
    Clock,
    Calendar,
    Percent,
    TrendingUp,
    Building2,
    ChevronDown,
    ChevronUp,
    BarChart3,
} from 'lucide-react';
import { bondService } from './services/bondService';
import { adminService } from '../admin/services/adminService';
import { useAuth } from '../auth/AuthContext';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import EmptyState from '../../shared/components/feedback/EmptyState';
import PageHeader from '../../shared/components/layout/PageHeader';
import MarketStatusBadge from '../../shared/components/layout/MarketStatusBadge';
import SearchInput from '../../shared/components/form/SearchInput';
import SortSelect from '../../shared/components/form/SortSelect';
import Pagination from '../../shared/components/form/Pagination';
import { toast } from '../../shared/components/feedback/Toast';
import FilterTabs from '../../shared/components/form/FilterTabs';
import useListParams from '../../shared/hooks/useListParams';
import { useTheme } from '../../shared/context/ThemeContext';
import { BOND_TYPE_LABELS, BOND_TYPE_COLORS, CHART_LINE_COLORS } from './lib/bondConstants';

const SORT_OPTIONS = [
    { id: 'simpleYield', label: 'Basit Getiri' },
    { id: 'couponRate', label: 'Kupon Oranı' },
    { id: 'baseIndex', label: 'Endeks Fiyat' },
    { id: 'maturityEnd', label: 'Vade Sonu' },
    { id: 'seriesCode', label: 'Seri Kodu' },
];

function RateHistoryChart({ isinCode, bondType }) {
    const { isDark } = useTheme();
    const { data: rateData, isLoading } = useQuery({
        queryKey: ['bondRateHistory', isinCode],
        queryFn: () => bondService.getRateHistory(isinCode),
    });

    if (isLoading) return <div className="h-48 flex items-center justify-center text-fg-muted text-xs">Grafik yükleniyor…</div>;
    if (!rateData || rateData.length === 0) return <div className="h-48 flex items-center justify-center text-fg-muted text-xs">Rate verisi yok</div>;

    const lineColor = CHART_LINE_COLORS[bondType] || '#8b5cf6';

    const option = {
        grid: { top: 12, right: 12, bottom: 24, left: 40, containLabel: true },
        xAxis: {
            type: 'category',
            data: rateData.map(d => d.date),
            boundaryGap: false,
            axisLabel: { 
                color: isDark ? '#9ca3af' : '#6b7280',
                fontSize: 12,
            },
            axisLine: { lineStyle: { color: isDark ? '#374151' : '#e5e7eb' } },
        },
        yAxis: {
            type: 'value',
            axisLabel: {
                color: isDark ? '#9ca3af' : '#6b7280',
                fontSize: 12,
                formatter: (val) => `%${val.toFixed(2)}`,
            },
            axisLine: { lineStyle: { color: isDark ? '#374151' : '#e5e7eb' } },
            splitLine: { lineStyle: { color: isDark ? '#1f2937' : '#f3f4f6' } },
        },
        tooltip: {
            trigger: 'axis',
            backgroundColor: isDark ? '#1f2937' : '#fff',
            borderColor: isDark ? '#374151' : '#e5e7eb',
            textStyle: { color: isDark ? '#f3f4f6' : '#1f2937' },
            formatter: (params) => {
                if (!params.length) return '';
                const p = params[0];
                return `${p.axisValue}<br/>${p.marker}${p.seriesName}: ${Number(p.value).toFixed(4)}%`;
            },
        },
        series: [{
            name: 'Kupon Oranı',
            type: 'line',
            data: rateData.map(d => Number(d.rate)),
            smooth: true,
            lineStyle: { width: 2, color: lineColor },
            itemStyle: { color: lineColor },
            areaStyle: { color: `${lineColor}20` },
            symbol: 'none',
        }],
    };

    return (
        <div className="h-48">
            <ReactECharts option={option} style={{ height: '100%' }} />
        </div>
    );
}

function BondCard({ bond, isExpanded, onToggleChart }) {
    const maturityDays = daysUntil(bond.maturityEnd);
    const couponDays = daysUntil(bond.nextCouponDate);
    const typeColor = BOND_TYPE_COLORS[bond.bondType] || 'bg-accent/10 text-accent border-accent/20';

    return (
        <motion.div
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
                    <StatCell icon={Building2} label="İhraççı" value={bond.issuer || 'HAZİNE'} />
                    <StatCell icon={Percent} label="Kupon Oranı" value={formatRate(bond.couponRate)} mono />
                    <StatCell icon={TrendingUp} label="Basit Getiri" value={
                        isFloatingType(bond.bondType)
                            ? <span className="text-warning font-medium">Değişken</span>
                            : formatRate(bond.simpleYield)
                    } mono />
                    <StatCell icon={Calendar} label="Başlangıç" value={formatDate(bond.maturityStart)} mono />
                    <StatCell icon={Calendar} label="Vade Sonu" value={
                        <>{formatDate(bond.maturityEnd)}{maturityDays != null && <span className="ml-1.5 text-fg-subtle">({maturityDays}g)</span>}</>
                    } mono />
                    {bond.nextCouponDate && (
                        <StatCell icon={Calendar} label="Kupon Tarihi" value={
                            <>{formatDate(bond.nextCouponDate)}{couponDays != null && <span className="ml-1.5 text-fg-subtle">({couponDays}g)</span>}</>
                        } mono />
                    )}
                </div>

                <div className="mt-4 flex items-center justify-between">
                    <div className="flex items-center gap-1.5 text-[11px] text-fg-subtle">
                        <Clock className="h-3 w-3" />
                        {bond.lastUpdated ? new Date(bond.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                    </div>
                    {bond.couponRate != null && Number(bond.couponRate) > 0 && (
                        <button
                            onClick={() => onToggleChart(bond.seriesCode)}
                            className="flex items-center gap-1.5 text-xs text-fg-muted hover:text-accent transition-colors cursor-pointer bg-transparent border-none"
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
}

function StatCell({ icon: Icon, label, value, mono }) {
    return (
        <div className="space-y-1">
            <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                <Icon className="h-3.5 w-3.5" /> {label}
            </span>
            <span className={`block text-sm ${mono ? 'font-mono' : 'font-medium'} text-fg`}>{value}</span>
        </div>
    );
}

function formatRate(val) {
    if (val == null) return 'N/A';
    return `%${Number(val).toFixed(2)}`;
}

function formatPrice(val) {
    if (val == null) return 'N/A';
    return new Intl.NumberFormat('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(val);
}

function formatDate(val) {
    if (!val) return 'N/A';
    return new Date(val).toLocaleDateString('tr-TR', { timeZone: 'Europe/Istanbul' });
}

function daysUntil(dateStr) {
    if (!dateStr) return null;
    return Math.ceil((new Date(dateStr) - new Date()) / (1000 * 60 * 60 * 24));
}

function isFloatingType(bondType) {
    return ['FLOATING_TLREF', 'FLOATING_CPI', 'FLOATING_AUCTION', 'SUKUK_CPI'].includes(bondType);
}

export default function BondsPage() {
    const { hasRole } = useAuth();
    const [updating, setUpdating] = useState({});
    const [expandedBond, setExpandedBond] = useSessionState('bonds-expanded', null);
    const isAdmin = hasRole('ADMIN');
    const listParams = useListParams();
    const typeFilter = listParams.filter || 'ALL';

    const { data: bondTypes = [] } = useQuery({
        queryKey: ['bondTypes'],
        queryFn: bondService.getTypes,
        staleTime: 60_000,
    });

    const queryParams = {
        ...listParams.params,
        ...(typeFilter !== 'ALL' && { bondType: typeFilter }),
    };

    const { data, isLoading: loading, error, refetch } = useQuery({
        queryKey: ['bonds', queryParams],
        queryFn: () => bondService.getAllBonds(queryParams),
        placeholderData: (prev) => prev,
    });

    const bonds = data?.content || [];
    const totalPages = data?.totalPages || 0;
    const totalElements = data?.totalElements || 0;

    const handleBondUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerBondUpdate();
            toast.success('Güncelleme Başlatıldı', response.message || 'Tahvil güncelleme başlatıldı');
            setTimeout(refetch, 10000);
        } catch (err) {
            toast.error('Güncelleme Hatası', err.response?.data?.message || err.message);
        } finally {
            setUpdating(prev => ({ ...prev, full: false }));
        }
    };

    const adminActions = [
        { key: 'full', label: 'Güncelle', title: 'Tahvil verilerini güncelle', handler: handleBondUpdate },
    ];

    const handleTypeFilter = (id) => {
        listParams.setFilter(id);
    };

    if (loading && bonds.length === 0) return <LoadingState message="Tahvil verileri yükleniyor…" />;
    if (error) return <ErrorState message="Tahvil verileri yüklenirken hata oluştu" onRetry={refetch} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Landmark className="h-5 w-5" />}
                title={
                    <span className="inline-flex items-center gap-3 flex-wrap">
                        Tahvil & Bono
                        <MarketStatusBadge market="BOND" compact />
                    </span>
                }
                onRefresh={refetch}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

            <div className="flex flex-wrap items-center gap-3">
                <div className="w-48">
                    <SearchInput
                        value={listParams.search}
                        onChange={listParams.setSearch}
                        placeholder="ISIN veya seri kodu ara..."
                        withSuggestions
                        suggestFn={(q) => bondService.getAllBonds({ search: q, size: 6 }).then(r => r.content || [])}
                        suggestLabelFn={(b) => b.isinCode || b.seriesCode}
                    />
                </div>
                {totalElements > 0 && (
                    <span className="text-xs text-fg-muted">{totalElements} tahvil</span>
                )}
                <SortSelect
                    value={listParams.sort}
                    direction={listParams.direction}
                    options={SORT_OPTIONS}
                    onSortChange={listParams.setSort}
                    onDirectionChange={listParams.setDirection}
                />
            </div>

            {bondTypes.length > 0 && (
                <FilterTabs
                    items={bondTypes.map(b => ({ type: b.type, count: b.count, label: BOND_TYPE_LABELS[b.type] || b.type }))}
                    activeId={typeFilter}
                    onSelect={handleTypeFilter}
                    allCount={bondTypes.reduce((sum, b) => sum + Number(b.count), 0)}
                    layoutId="bond-type"
                />
            )}

            <AnimatePresence>
                {bonds.length > 0 ? (
                    <motion.div
                        variants={containerVariants(0.06)}
                        initial="hidden"
                        animate="show"
                        className="flex flex-col gap-4 min-h-[600px]"
                    >
                        {bonds.map((bond) => (
                            <BondCard
                                key={bond.seriesCode}
                                bond={bond}
                                isExpanded={expandedBond === bond.seriesCode}
                                onToggleChart={(code) => setExpandedBond(prev => prev === code ? null : code)}
                            />
                        ))}
                    </motion.div>
                ) : !loading && (
                    <EmptyState
                        message={listParams.search ? 'Aramayla eşleşen tahvil bulunamadı.' : 'Henüz tahvil verisi bulunmuyor.'}
                        hint={!listParams.search && isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : undefined}
                    />
                )}
            </AnimatePresence>

            <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />
        </div>
    );
}
