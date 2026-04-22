import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import {
    LineChart,
    Activity,
    Clock,
    Users as UsersIcon,
    Wallet,
} from 'lucide-react';
import { TrendingUp, TrendingDown, ShoppingCart } from '../../shared/components/AnimatedIcons';
import { fundService } from './fundService';
import { adminService } from '../admin/adminService';
import { useAuth } from '../auth/AuthContext';
import { formatPriceTRY, formatCompactTRY, formatVolume, getChangeClass, changeColors, changeBg, formatPercentAbs } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import EmptyState from '../../shared/components/EmptyState';
import PageHeader from '../../shared/components/PageHeader';
import SearchInput from '../../shared/components/SearchInput';
import SortSelect from '../../shared/components/SortSelect';
import Pagination from '../../shared/components/Pagination';
import BuyModal from '../../shared/components/BuyModal';
import FilterTabs from '../../shared/components/FilterTabs';
import { toast } from '../../shared/components/Toast';
import useListParams from '../../shared/hooks/useListParams';
import useMarketListData from '../../shared/hooks/useMarketListData';

const FUND_TYPE_LABELS = {
    BYF: 'Borsa Yatırım Fonları',
    YAT: 'Yatırım Fonları',
};

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

function FundsPage() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [buyTarget, setBuyTarget] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
    });
    const isAdmin = hasRole('ADMIN');
    const listParams = useListParams();
    const typeFilter = listParams.filter || 'ALL';

    const { data: fundTypes = [] } = useQuery({
        queryKey: ['fundTypes'],
        queryFn: fundService.getGroupCounts,
        staleTime: 60_000,
    });

    const queryParams = {
        ...listParams.params,
        ...(typeFilter !== 'ALL' && { subType: typeFilter }),
    };

    const { data, isLoading: loading, error, refetch } = useMarketListData(
        'funds', fundService.getAll, queryParams);

    const funds = data?.content || [];
    const totalPages = data?.totalPages || 0;
    const totalElements = data?.totalElements || 0;

    const handleTrigger = async (action, triggerFn, successMsg) => {
        setUpdating(prev => ({ ...prev, [action]: true }));
        try {
            const response = await triggerFn();
            toast.success('Güncelleme Başlatıldı', response.message || successMsg);
            if (action !== 'candles') setTimeout(refetch, 5000);
        } catch (err) {
            toast.error('Güncelleme Hatası', err.response?.data?.message || err.message);
        } finally {
            setUpdating(prev => ({ ...prev, [action]: false }));
        }
    };

    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'Fon snapshot verilerini güncelle', handler: () => handleTrigger('snapshot', adminService.triggerFundSnapshot, 'Fon snapshot güncelleme başlatıldı') },
        { key: 'candles', label: 'Candles', title: 'Fon mum verilerini güncelle', handler: () => handleTrigger('candles', adminService.triggerFundCandles, 'Fon candle güncelleme başlatıldı') },
        { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + candles)', handler: () => handleTrigger('full', adminService.triggerFundFull, 'Fon tam güncelleme başlatıldı') },
    ];

    if (loading && funds.length === 0) return <LoadingState message="Fon verileri yükleniyor…" />;
    if (error) return <ErrorState message="Fon verileri yüklenirken hata oluştu" onRetry={refetch} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<LineChart className="h-5 w-5" />}
                title="Yatırım Fonları"
                onRefresh={refetch}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

            <div className="flex flex-wrap items-center gap-3">
                <div className="w-48">
                    <SearchInput value={listParams.search} onChange={listParams.setSearch} placeholder="Fon ara..." withSuggestions filterType="FUND" />
                </div>
                {totalElements > 0 && (
                    <span className="text-xs text-fg-muted">{totalElements} fon</span>
                )}
                <SortSelect
                    value={listParams.sort}
                    direction={listParams.direction}
                    options={SORT_OPTIONS}
                    onSortChange={listParams.setSort}
                    onDirectionChange={listParams.setDirection}
                />
                {fundTypes.length > 0 && <FilterTabs
                    items={fundTypes.map(f => ({ type: f.type, count: f.count, label: FUND_TYPE_LABELS[f.type] || f.type }))}
                    activeId={typeFilter}
                    onSelect={(id) => listParams.setFilter(id)}
                    allCount={fundTypes.reduce((sum, f) => sum + Number(f.count), 0)}
                    layoutId="fund-type"
                />}
            </div>

            <AnimatePresence>
                {funds.length > 0 ? (
                    <motion.div
                        variants={containerVariants(0.06)}
                        initial="hidden"
                        animate="show"
                        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
                    >
                        {funds.map((fund) => {
                            const meta = fund.metadata || {};
                            const cls = getChangeClass(fund.changePercent);
                            return (
                            <motion.div
                                key={fund.code}
                                variants={cardVariants}
                                onClick={() => navigate(`/funds/${fund.code}`)}
                                className="group cursor-pointer rounded-2xl border border-border-default bg-bg-elevated p-5 card-hover transition-all duration-200 hover:border-border-hover overflow-hidden min-w-0 relative"
                            >
                                <div className="flex items-start justify-between gap-2 min-w-0">
                                    <div className="flex items-center gap-3 min-w-0 flex-1">
                                        <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent shrink-0">
                                            <LineChart className="w-4.5 h-4.5" />
                                        </span>
                                        <div className="min-w-0">
                                            <h3 className="truncate text-sm font-semibold text-fg">{fund.code}</h3>
                                            <span className="block truncate text-xs text-fg-muted">{fund.name || fund.code}</span>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2 shrink-0">
                                        <span className="rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
                                            {meta.fundType || 'FON'}
                                        </span>
                                        <button
                                            onClick={(e) => {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                setBuyTarget({ assetCode: fund.code, assetName: fund.name || fund.code, price: fund.price });
                                            }}
                                            title="Satın Al"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                        >
                                            <ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />
                                        </button>
                                    </div>
                                </div>

                                <div className="mt-3 space-y-1">
                                    <span className="block truncate font-mono text-xl font-bold text-fg">
                                        {formatPriceTRY(fund.price)}
                                    </span>
                                    {meta.fundType === 'BYF' && meta.bulletinPrice != null && (
                                        <div className="flex items-center gap-2 text-xs text-fg-muted">
                                            <span className="font-medium">Borsa Fiyatı</span>
                                            <span className="font-mono">{formatPriceTRY(meta.bulletinPrice)}</span>
                                        </div>
                                    )}
                                </div>

                                {fund.changePercent != null && (
                                    <div className={`mt-2 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                        {fund.changePercent > 0 ? (
                                            <TrendingUp className="h-3.5 w-3.5" />
                                        ) : fund.changePercent < 0 ? (
                                            <TrendingDown className="h-3.5 w-3.5" />
                                        ) : null}
                                        {formatPercentAbs(fund.changePercent)}
                                        <span className="ml-1 opacity-75">24h</span>
                                    </div>
                                )}

                                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                                    {meta.fundType === 'YAT' && meta.investorCount != null && (
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="flex items-center gap-1 text-fg-muted">
                                                <UsersIcon className="h-3 w-3" />
                                                Yatırımcı
                                            </span>
                                            <span className="font-mono text-fg">{formatVolume(meta.investorCount)}</span>
                                        </div>
                                    )}
                                    <div className="flex items-center justify-between text-xs">
                                        <span className="flex items-center gap-1 text-fg-muted">
                                            <Wallet className="h-3 w-3" />
                                            Portföy
                                        </span>
                                        <span className="font-mono text-fg">{formatCompactTRY(meta.portfolioSize)}</span>
                                    </div>
                                    <div className="flex items-center justify-between text-xs">
                                        <span className="flex items-center gap-1 text-fg-muted">
                                            <Activity className="h-3 w-3" />
                                            Pay Sayısı
                                        </span>
                                        <span className="font-mono text-fg">{formatVolume(meta.shareCount)}</span>
                                    </div>
                                </div>

                                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                                    <Clock className="h-3 w-3" />
                                    {fund.lastUpdated ? new Date(fund.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                                </div>
                            </motion.div>
                            );
                        })}
                    </motion.div>
                ) : (
                    <EmptyState
                        icon={<LineChart className="h-7 w-7 text-fg-subtle" />}
                        message={listParams.search ? 'Aramayla eşleşen fon bulunamadı.' : 'Henüz fon verisi bulunmuyor'}
                        hint={!listParams.search && isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : undefined}
                    />
                )}
            </AnimatePresence>

            <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />

            {buyTarget && (
                <BuyModal
                    assetType="FUND"
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

export default FundsPage;
