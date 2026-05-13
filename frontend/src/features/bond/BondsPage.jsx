import { useState } from 'react';
import { STALE } from '../../shared/constants/query';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import useSessionState from "../../shared/hooks/useSessionState";
import { AnimatePresence } from 'framer-motion';
import ReactECharts from 'echarts-for-react';
import { useTranslation } from 'react-i18next';
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
import { useAuth } from '../auth/useAuth';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import EmptyState from '../../shared/components/feedback/EmptyState';
import PageHeader from '../../shared/components/layout/PageHeader';
import MarketStatusBadge from '../../shared/components/layout/MarketStatusBadge';
import SearchInput from '../../shared/components/form/SearchInput';
import SortSelect from '../../shared/components/form/SortSelect';
import Pagination from '../../shared/components/form/Pagination';
import { toast } from '../../shared/components/feedback/toastBus';
import FilterTabs from '../../shared/components/form/FilterTabs';
import useListParams from '../../shared/hooks/useListParams';
import { useTheme } from '../../shared/context/useTheme';
import { BOND_TYPE_COLORS, CHART_LINE_COLORS } from './lib/bondConstants';

const SORT_OPTION_IDS = ['simpleYield', 'couponRate', 'baseIndex', 'maturityEnd', 'seriesCode'];

function RateHistoryChart({ isinCode, bondType }) {
    const { t } = useTranslation();
    const { isDark } = useTheme();
    const { data: rateData, isLoading } = useQuery({
        queryKey: ['bondRateHistory', isinCode],
        queryFn: () => bondService.getRateHistory(isinCode),
    });

    if (isLoading) return <div className="h-48 flex items-center justify-center text-fg-muted text-xs">{t('market.bond.chartLoading')}</div>;
    if (!rateData || rateData.length === 0) return <div className="h-48 flex items-center justify-center text-fg-muted text-xs">{t('market.bond.noRateData')}</div>;

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
            name: t('market.bond.couponRate'),
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
    const { t } = useTranslation();
    const localeTag = t('common.localeTag');
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
                            {t(`market.bond.types.${bond.bondType}`, { defaultValue: bond.bondType })}
                        </span>
                        <span className="font-mono text-2xl font-bold text-fg">
                            {formatPrice(bond.baseIndex, localeTag)}
                        </span>
                    </div>
                </div>

                <div className="mt-5 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4">
                    <StatCell icon={Building2} label={t('market.bond.issuerLabel')} value={bond.issuer || t('market.bond.treasuryFallback')} />
                    <StatCell icon={Percent} label={t('market.bond.couponRate')} value={formatRate(bond.couponRate)} mono />
                    <StatCell icon={TrendingUp} label={t('market.bond.simpleYield')} value={
                        isFloatingType(bond.bondType)
                            ? <span className="text-warning font-medium">{t('market.bond.floating')}</span>
                            : formatRate(bond.simpleYield)
                    } mono />
                    <StatCell icon={Calendar} label={t('market.bond.startLabel')} value={formatDate(bond.maturityStart, localeTag)} mono />
                    <StatCell icon={Calendar} label={t('market.bond.maturityLabel')} value={
                        <>{formatDate(bond.maturityEnd, localeTag)}{maturityDays != null && <span className="ml-1.5 text-fg-subtle">({maturityDays}{t('market.bond.daysSuffix')})</span>}</>
                    } mono />
                    {bond.nextCouponDate && (
                        <StatCell icon={Calendar} label={t('market.bond.couponDateLabel')} value={
                            <>{formatDate(bond.nextCouponDate, localeTag)}{couponDays != null && <span className="ml-1.5 text-fg-subtle">({couponDays}{t('market.bond.daysSuffix')})</span>}</>
                        } mono />
                    )}
                </div>

                <div className="mt-4 flex items-center justify-between">
                    <div className="flex items-center gap-1.5 text-[11px] text-fg-subtle">
                        <Clock className="h-3 w-3" />
                        {bond.lastUpdated ? new Date(bond.lastUpdated).toLocaleString(localeTag, { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                    </div>
                    {bond.couponRate != null && Number(bond.couponRate) > 0 && (
                        <button
                            onClick={() => onToggleChart(bond.seriesCode)}
                            className="flex items-center gap-1.5 text-xs text-fg-muted hover:text-accent transition-colors cursor-pointer bg-transparent border-none"
                        >
                            <BarChart3 className="h-3.5 w-3.5" />
                            {t('market.bond.rateChangeButton')}
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

function formatPrice(val, localeTag = 'en-US') {
    if (val == null) return 'N/A';
    return new Intl.NumberFormat(localeTag, { minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(val);
}

function formatDate(val, localeTag = 'en-US') {
    if (!val) return 'N/A';
    return new Date(val).toLocaleDateString(localeTag, { timeZone: 'Europe/Istanbul' });
}

function daysUntil(dateStr) {
    if (!dateStr) return null;
    return Math.ceil((new Date(dateStr) - new Date()) / (1000 * 60 * 60 * 24));
}

function isFloatingType(bondType) {
    return ['FLOATING_TLREF', 'FLOATING_CPI', 'FLOATING_AUCTION', 'SUKUK_CPI'].includes(bondType);
}

export default function BondsPage() {
    const { t } = useTranslation();
    const { hasRole } = useAuth();
    const [updating, setUpdating] = useState({});
    const [expandedBond, setExpandedBond] = useSessionState('bonds-expanded', null);
    const isAdmin = hasRole('ADMIN');
    const listParams = useListParams();
    const typeFilter = listParams.filter || 'ALL';
    const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`market.bond.sort.${id}`) }));

    const { data: bondTypes = [] } = useQuery({
        queryKey: ['bondTypes'],
        queryFn: bondService.getTypes,
        staleTime: STALE.MEDIUM,
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
            toast.success(t('market.bond.updateStartedTitle'), response.message || t('market.bond.updateStarted'));
            setTimeout(refetch, 10000);
        } catch (err) {
            toast.error(t('market.bond.updateErrorTitle'), err.response?.data?.message || err.message);
        } finally {
            setUpdating(prev => ({ ...prev, full: false }));
        }
    };

    const adminActions = [
        { key: 'full', label: t('market.bond.updateLabel'), title: t('market.bond.updateTitle'), handler: handleBondUpdate },
    ];

    const handleTypeFilter = (id) => {
        listParams.setFilter(id);
    };

    if (loading && bonds.length === 0) return <LoadingState message={t('market.bond.loading')} />;
    if (error) return <ErrorState message={t('market.bond.error')} onRetry={refetch} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Landmark className="h-5 w-5" />}
                title={
                    <span className="inline-flex items-center gap-3 flex-wrap">
                        {t('market.bond.title')}
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
                        placeholder={t('market.bond.searchPlaceholder')}
                        withSuggestions
                        suggestFn={(q) => bondService.getAllBonds({ search: q, size: 6 }).then(r => r.content || [])}
                        suggestLabelFn={(b) => b.isinCode || b.seriesCode}
                    />
                </div>
                {totalElements > 0 && (
                    <span className="text-xs text-fg-muted">{totalElements} {t('market.bond.countLabel')}</span>
                )}
                <SortSelect
                    value={listParams.sort}
                    direction={listParams.direction}
                    options={sortOptions}
                    onSortChange={listParams.setSort}
                    onDirectionChange={listParams.setDirection}
                />
            </div>

            {bondTypes.length > 0 && (
                <FilterTabs
                    items={bondTypes.map(b => ({ type: b.type, count: b.count, label: t(`market.bond.types.${b.type}`, { defaultValue: b.type }) }))}
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
                        message={listParams.search ? t('market.bond.noSearchResults') : t('market.bond.empty')}
                        hint={!listParams.search && isAdmin ? t('market.empty.adminHint') : undefined}
                    />
                )}
            </AnimatePresence>

            <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />
        </div>
    );
}
