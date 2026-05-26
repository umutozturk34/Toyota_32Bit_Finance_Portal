import { useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { STALE } from '../../shared/constants/query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LineChart, Tag, Activity, Clock, Users as UsersIcon, Wallet, X } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../shared/components/feedback/AnimatedIcons';
import { fundService } from './services/fundService';
import { adminService } from '../admin/services/adminService';
import { formatVolume } from '../../shared/utils/formatters';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/asset/ChangePercentBadge';
import RiskBadge from '../../shared/components/asset/RiskBadge';
import useListParams from '../../shared/hooks/useListParams';
import { useMoney } from '../../shared/hooks/useMoney';

const SORT_OPTION_IDS = ['changePercent', 'price', 'bulletinPrice', 'portfolioSize', 'investorCount', 'name'];
const FUND_TYPE_IDS = ['BYF', 'YAT'];

function FundsPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const listParams = useListParams();
    const { format: money, formatCompact: moneyCompact } = useMoney();
    const typeFilter = listParams.filter || 'ALL';
    const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`market.sort.${id}`) }));
    const fundTypeLabel = (id) => FUND_TYPE_IDS.includes(id) ? t(`market.fund.types.${id}`) : id;

    const { data: fundTypes = [] } = useQuery({
        queryKey: ['fundTypes'],
        queryFn: fundService.getGroupCounts,
        staleTime: STALE.MEDIUM,
    });

    const { data: subCategoryOptions = [] } = useQuery({
        queryKey: ['fundSubCategories'],
        queryFn: fundService.getSubCategories,
        staleTime: STALE.LONG,
    });

    const [searchParams, setSearchParams] = useSearchParams();
    const selectedSubCategories = useMemo(() => {
        const raw = searchParams.get('cats');
        return raw ? raw.split(',').filter(Boolean) : [];
    }, [searchParams]);
    const selectedRisks = useMemo(() => {
        const raw = searchParams.get('risk');
        return raw ? raw.split(',').map(Number).filter(n => !Number.isNaN(n)) : [];
    }, [searchParams]);

    const updateFilters = useCallback((updates) => {
        setSearchParams((prev) => {
            const next = new URLSearchParams(prev);
            if (updates.cats !== undefined) {
                if (updates.cats.length === 0) next.delete('cats');
                else next.set('cats', updates.cats.join(','));
            }
            if (updates.risk !== undefined) {
                if (updates.risk.length === 0) next.delete('risk');
                else next.set('risk', updates.risk.join(','));
            }
            return next;
        }, { replace: true });
    }, [setSearchParams]);

    const queryParams = useMemo(() => ({
        ...listParams.params,
        ...(typeFilter !== 'ALL' && { subType: typeFilter }),
        ...(selectedSubCategories.length > 0 && { subCategory: selectedSubCategories }),
        ...(selectedRisks.length > 0 && { riskValue: selectedRisks }),
    }), [listParams.params, typeFilter, selectedSubCategories, selectedRisks]);

    const toggleSubCat = useCallback((cat) => {
        const next = selectedSubCategories.includes(cat)
            ? selectedSubCategories.filter(c => c !== cat)
            : [...selectedSubCategories, cat];
        updateFilters({ cats: next });
    }, [selectedSubCategories, updateFilters]);
    const toggleRisk = useCallback((r) => {
        const next = selectedRisks.includes(r)
            ? selectedRisks.filter(x => x !== r)
            : [...selectedRisks, r];
        updateFilters({ risk: next });
    }, [selectedRisks, updateFilters]);
    const clearFilters = useCallback(() => updateFilters({ cats: [], risk: [] }), [updateFilters]);
    const hasActiveFilters = selectedSubCategories.length > 0 || selectedRisks.length > 0;

    const filterToolbar = (
        <div className="mb-3 space-y-2">
            <div className="flex items-start gap-2 flex-wrap">
                <span className="text-[11px] font-semibold text-fg-muted uppercase tracking-wider pt-1.5">{t('market.fund.filterCategoryLabel')}</span>
                <div className="flex flex-wrap gap-1">
                    {subCategoryOptions.map(cat => {
                        const active = selectedSubCategories.includes(cat);
                        return (
                            <button
                                key={cat}
                                type="button"
                                onClick={() => toggleSubCat(cat)}
                                className={`px-2 py-0.5 text-[11px] rounded-md border transition-colors ${
                                    active
                                        ? 'border-accent/40 bg-accent/15 text-accent-bright font-semibold'
                                        : 'border-border-default bg-bg-base/40 text-fg-muted hover:text-fg hover:border-border-strong'
                                }`}
                            >
                                {t(`fundCategory.${cat}`, { defaultValue: cat })}
                            </button>
                        );
                    })}
                </div>
            </div>
            <div className="flex items-center gap-2 flex-wrap">
                <span className="text-[11px] font-semibold text-fg-muted uppercase tracking-wider">{t('market.fund.filterRiskLabel')}</span>
                <div className="flex gap-1">
                    {[1, 2, 3, 4, 5, 6, 7].map(r => {
                        const active = selectedRisks.includes(r);
                        return (
                            <button
                                key={r}
                                type="button"
                                onClick={() => toggleRisk(r)}
                                className={`w-7 h-7 text-[11px] rounded-md border font-semibold transition-colors ${
                                    active
                                        ? 'border-accent/40 bg-accent/15 text-accent-bright'
                                        : 'border-border-default bg-bg-base/40 text-fg-muted hover:text-fg'
                                }`}
                                title={`Risk ${r}`}
                            >
                                {r}
                            </button>
                        );
                    })}
                </div>
                {hasActiveFilters && (
                    <button
                        type="button"
                        onClick={clearFilters}
                        className="ml-2 inline-flex items-center gap-1 text-[11px] text-fg-muted hover:text-fg"
                    >
                        <X className="h-3 w-3" />
                        {t('market.fund.clearFilters')}
                    </button>
                )}
            </div>
        </div>
    );

    const renderCard = (fund, { setBuyTarget }) => {
        const meta = fund.metadata || {};
        const oneYear = meta.return1y;
        const rawCategory = meta.category || meta.subCategory;
        const categoryLabel = rawCategory ? t(`fundCategory.${rawCategory}`, { defaultValue: rawCategory }) : null;
        return (
            <AssetCard
                key={fund.code}
                onClick={() => navigate(`/funds/${fund.code}`)}
                className="overflow-hidden min-w-0 relative"
            >
                <div className="flex items-start justify-between gap-2 min-w-0">
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                        <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent shrink-0">
                            <LineChart className="w-4.5 h-4.5" />
                        </span>
                        <div className="min-w-0">
                            <h3 className="truncate text-sm font-semibold text-fg">{fund.code}</h3>
                            <span className="block text-xs text-fg-muted leading-snug truncate" title={fund.name || fund.code}>{fund.name || fund.code}</span>
                        </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                        <span className="rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
                            {meta.fundType ? t(`market.fund.shortType.${meta.fundType}`, { defaultValue: meta.fundType }) : t('market.fund.fallbackBadge')}
                        </span>
                        <AssetBuyButton
                            onClick={() => setBuyTarget({ assetCode: fund.code, assetName: fund.name || fund.code, price: fund.price })}
                        />
                    </div>
                </div>

                {(categoryLabel || meta.riskValue != null) && (
                    <div className="mt-2 flex items-center gap-2 flex-wrap">
                        {categoryLabel && (
                            <div className="inline-flex items-center gap-1 rounded-md border border-accent/20 bg-accent/5 px-2 py-0.5 max-w-full min-w-0">
                                <Tag className="h-3 w-3 text-accent shrink-0" />
                                <span className="truncate text-[11px] font-medium text-accent-bright">{categoryLabel}</span>
                            </div>
                        )}
                        {meta.riskValue != null && <RiskBadge value={meta.riskValue} />}
                    </div>
                )}

                <div className="mt-3 flex items-end justify-between gap-3">
                    <div className="min-w-0">
                        <span className="block truncate font-mono text-xl font-bold text-fg">{money(fund.price)}</span>
                        {meta.fundType === 'BYF' && meta.bulletinPrice != null && (
                            <div className="flex items-center gap-2 text-xs text-fg-muted mt-0.5">
                                <span className="font-medium">{t('market.fund.exchangePriceLabel')}</span>
                                <span className="font-mono">{money(meta.bulletinPrice)}</span>
                            </div>
                        )}
                        <ChangePercentBadge
                            value={fund.changePercent}
                            positiveIcon={<TrendingUp className="h-3 w-3" />}
                            negativeIcon={<TrendingDown className="h-3 w-3" />}
                            size="sm"
                            className="mt-1"
                        >
                            <span className="ml-1 opacity-75">{t('market.fund.dayBadge')}</span>
                        </ChangePercentBadge>
                    </div>
                    {oneYear != null && (
                        <div className="text-right shrink-0">
                            <span className={`block font-mono text-base font-semibold ${oneYear >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                                {oneYear >= 0 ? '+' : ''}{Number(oneYear).toFixed(2)}%
                            </span>
                            <span className="block text-[10px] uppercase tracking-wider text-fg-subtle">{t('market.fund.return1yLabel')}</span>
                        </div>
                    )}
                </div>

                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                    {meta.fundType === 'YAT' && meta.investorCount != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><UsersIcon className="h-3 w-3" />{t('market.fund.investorLabel')}</span>
                            <span className="font-mono text-fg">{formatVolume(meta.investorCount)}</span>
                        </div>
                    )}
                    {meta.portfolioSize != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><Wallet className="h-3 w-3" />{t('market.fund.portfolioLabel')}</span>
                            <span className="font-mono text-fg">{moneyCompact(meta.portfolioSize)}</span>
                        </div>
                    )}
                    {meta.shareCount != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><Activity className="h-3 w-3" />{t('market.fund.shareCountLabel')}</span>
                            <span className="font-mono text-fg">{formatVolume(meta.shareCount)}</span>
                        </div>
                    )}
                    {meta.categoryRank != null && meta.categoryTotalFunds != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-fg-muted">{t('market.fund.rankLabel')}</span>
                            <span className="font-mono text-fg">{meta.categoryRank}/{meta.categoryTotalFunds}</span>
                        </div>
                    )}
                </div>

                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {fund.lastUpdated ? new Date(fund.lastUpdated).toLocaleString(t('common.localeTag'), { timeZone: 'Europe/Istanbul' }) : '—'}
                </div>
            </AssetCard>
        );
    };

    return (
        <MarketListPage
            title={t('market.fund.title')}
            icon={<LineChart className="h-5 w-5" />}
            emptyIcon={<LineChart className="h-7 w-7 text-fg-subtle" />}
            marketType="FUND"
            service={fundService}
            queryKey="funds"
            listParams={listParams}
            queryParams={queryParams}
            searchPlaceholder={t('market.fund.searchPlaceholder')}
            countLabel={t('market.fund.countLabel')}
            sortOptions={sortOptions}
            filterConfig={{
                tabItems: fundTypes.map(f => ({ type: f.type, count: f.count, label: fundTypeLabel(f.type) })),
                activeId: typeFilter,
                onSelect: (id) => listParams.setFilter(id),
                layoutId: 'fund-type',
            }}
            adminTriggers={[
                { key: 'snapshot', label: t('market.admin.snapshot'), title: t('market.admin.snapshotTitle'), fn: adminService.triggerFundSnapshot, successMsg: t('market.admin.snapshotStarted'), refetchDelay: 5000 },
                { key: 'candles', label: t('market.admin.candles'), title: t('market.admin.candlesTitle'), fn: adminService.triggerFundCandles, successMsg: t('market.admin.candlesStarted') },
                { key: 'full', label: t('market.admin.full'), title: t('market.admin.fullTitle'), fn: adminService.triggerFundFull, successMsg: t('market.admin.fullStarted'), refetchDelay: 5000 },
            ]}
            renderCard={renderCard}
            preGridChildren={filterToolbar}
            loadingMessage={t('market.fund.loading')}
            errorMessage={t('market.fund.error')}
            emptyMessage={t('market.fund.empty')}
            emptyHint={t('market.empty.adminHint')}
            gridClass="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
            animatePresence
        />
    );
}

export default FundsPage;
