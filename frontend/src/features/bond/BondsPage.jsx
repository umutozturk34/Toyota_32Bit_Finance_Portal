import { useState, useMemo } from 'react';
import { STALE } from '../../shared/constants/query';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import useSessionState from '../../shared/hooks/useSessionState';
import { useTranslation } from 'react-i18next';
import {
    Landmark,
    Clock,
    Calendar,
    Percent,
    TrendingUp,
    Building2,
    ChevronRight,
    Filter,
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
import { BOND_TYPE_COLORS } from './lib/bondConstants';

const SORT_OPTION_IDS = ['simpleYield', 'couponRate', 'baseIndex', 'maturityEnd', 'seriesCode'];

function BondCard({ bond, onClick }) {
    const { t } = useTranslation();
    const localeTag = t('common.localeTag');
    const maturityDays = daysUntil(bond.maturityEnd);
    const couponDays = daysUntil(bond.nextCouponDate);
    const typeColor = BOND_TYPE_COLORS[bond.bondType] || 'bg-accent/10 text-accent border-accent/20';

    return (
        <motion.div
            variants={cardVariants}
            layout
            role="button"
            tabIndex={0}
            onClick={onClick}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } }}
            className="rounded-2xl border border-border-default bg-bg-elevated card-hover transition-all duration-200 hover:border-accent/40 overflow-hidden cursor-pointer focus:outline-none focus:ring-2 focus:ring-accent/40"
        >
            <div className="p-4 sm:p-6">
                <div className="flex items-start justify-between gap-3 flex-wrap">
                    <div className="flex items-center gap-3 sm:gap-4 min-w-0 flex-1">
                        <span className="flex items-center justify-center w-10 h-10 sm:w-12 sm:h-12 rounded-xl bg-accent/10 text-accent shrink-0">
                            <Landmark className="w-5 h-5 sm:w-6 sm:h-6" />
                        </span>
                        <div className="min-w-0">
                            <h3 className="text-base sm:text-lg font-bold text-fg truncate">{bond.isinCode}</h3>
                            <span className="block text-xs sm:text-sm text-fg-muted truncate">{bond.seriesCode}</span>
                        </div>
                    </div>
                    <div className="flex items-center gap-2 sm:gap-3 shrink-0 flex-wrap">
                        <span className={`rounded-lg border px-2 sm:px-3 py-1 text-[10px] sm:text-xs font-semibold tracking-wider ${typeColor}`}>
                            {t(`market.bond.types.${bond.bondType}`, { defaultValue: bond.bondType })}
                        </span>
                        <span className="font-mono text-lg sm:text-2xl font-bold text-fg">
                            {formatPrice(bond.baseIndex, localeTag)}
                        </span>
                        <ChevronRight className="h-4 w-4 text-fg-subtle" />
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

                <div className="mt-4 flex items-center gap-1.5 text-[11px] text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {bond.lastUpdated ? new Date(bond.lastUpdated).toLocaleString(localeTag, { timeZone: 'Europe/Istanbul' }) : '—'}
                </div>
            </div>
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
    if (val == null) return '—';
    return `%${Number(val).toFixed(2)}`;
}

function formatPrice(val, localeTag = 'en-US') {
    if (val == null) return '—';
    return new Intl.NumberFormat(localeTag, { minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(val);
}

function formatDate(val, localeTag = 'en-US') {
    if (!val) return '—';
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
    const navigate = useNavigate();
    const [updating, setUpdating] = useState({});
    const [activeOnly, setActiveOnly] = useSessionState('bonds-active-only', true);
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

    const allBonds = useMemo(() => data?.content || [], [data]);
    const totalPages = data?.totalPages || 0;
    const totalElements = data?.totalElements || 0;

    const todayIso = useMemo(() => new Date().toISOString().slice(0, 10), []);
    const visibleBonds = useMemo(() => {
        if (!activeOnly) return allBonds;
        return allBonds.filter((b) => !b.maturityEnd || b.maturityEnd >= todayIso);
    }, [allBonds, activeOnly, todayIso]);
    const hiddenCount = allBonds.length - visibleBonds.length;

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

    if (loading && allBonds.length === 0) return <LoadingState message={t('market.bond.loading')} />;
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
                <button
                    type="button"
                    onClick={() => setActiveOnly((v) => !v)}
                    className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-semibold transition-colors cursor-pointer ${
                        activeOnly
                            ? 'border-accent/40 bg-accent/10 text-accent'
                            : 'border-border-default bg-bg-elevated text-fg-muted hover:text-fg'
                    }`}
                    title={t('market.bond.activeOnlyTooltip', { defaultValue: 'Vade dolmamış bonolar' })}
                >
                    <Filter className="h-3.5 w-3.5" />
                    {t('market.bond.activeOnlyLabel', { defaultValue: 'Açık bonolar' })}
                    {activeOnly && hiddenCount > 0 && (
                        <span className="ml-1 rounded bg-accent/20 px-1.5 py-0.5 text-[10px] font-mono">
                            +{hiddenCount}
                        </span>
                    )}
                </button>
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
                {visibleBonds.length > 0 ? (
                    <motion.div
                        variants={containerVariants(0.06)}
                        initial="hidden"
                        animate="show"
                        className="flex flex-col gap-4 min-h-[600px]"
                    >
                        {visibleBonds.map((bond) => (
                            <BondCard
                                key={bond.seriesCode}
                                bond={bond}
                                onClick={() => navigate(`/bonds/${encodeURIComponent(bond.seriesCode)}`)}
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
