import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
    BarChart2,
    ChevronUp,
    ChevronDown,
    Activity,
    Clock,
} from 'lucide-react';
import { TrendingUp, TrendingDown, ShoppingCart } from '../../shared/components/AnimatedIcons';
import { stockService } from './stockService';
import { adminService } from '../admin/adminService';
import { useAuth } from '../auth/AuthContext';
import { getChangeClass, changeColors, changeBg, formatPrice, formatVolume } from '../../shared/utils/formatters';
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
import { assetCodeLabel } from '../../shared/utils/assetCode';

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

const SEGMENT_LABELS = {
    MAIN_INDEX: 'Ana Endeksler',
    SECONDARY_INDEX: 'Alt Endeksler',
    EQUITY: 'Hisseler',
};

function StocksPage() {
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
    const segment = listParams.filter || 'EQUITY';

    const { data: segmentCounts = [] } = useQuery({
        queryKey: ['stockSegments'],
        queryFn: stockService.getSegmentCounts,
        staleTime: 60_000,
    });

    const { data: indicesData } = useQuery({
        queryKey: ['stocks', 'indices'],
        queryFn: () => stockService.getAllStocks({ segment: 'MAIN_INDEX', size: 10 }),
    });
    const indices = indicesData?.content || [];

    const tabItems = segmentCounts
        .filter(s => s.type !== 'MAIN_INDEX')
        .map(s => ({ type: s.type, count: s.count, label: SEGMENT_LABELS[s.type] || s.type }));
    const tabTotal = tabItems.reduce((sum, t) => sum + Number(t.count), 0);

    const queryParams = {
        ...listParams.params,
        segment,
    };

    const { data, isLoading: loading, isFetching, error, refetch } = useQuery({
        queryKey: ['stocks', queryParams],
        queryFn: () => stockService.getAllStocks(queryParams),
        placeholderData: (prev) => prev,
    });

    const stocks = data?.content || [];
    const totalPages = data?.totalPages || 0;
    const totalElements = data?.totalElements || 0;

    const handleSegmentChange = (id) => {
        listParams.setFilter(id);
    };

    const handleTrigger = async (action, triggerFn, successMsg) => {
        setUpdating(prev => ({ ...prev, [action]: true }));
        try {
            const response = await triggerFn();
            toast.success('Güncelleme Başlatıldı', response.message || successMsg);
            if (action === 'snapshot') setTimeout(refetch, 5000);
        } catch (err) {
            toast.error('Güncelleme Hatası', err.response?.data?.message || err.message);
        } finally {
            setUpdating(prev => ({ ...prev, [action]: false }));
        }
    };

    const formatStockPrice = (price) => formatPrice(price, { locale: 'tr-TR' });

    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'Hisse snapshot verilerini güncelle (fiyat, hacim vb.)', handler: () => handleTrigger('snapshot', adminService.triggerStockSnapshot, 'Hisse snapshot güncelleme başlatıldı') },
        { key: 'candles', label: 'Candles (5y)', title: '5 yıllık OHLC verilerini güncelle (10-15 dakika)', handler: () => handleTrigger('candles', adminService.triggerStockCandles, 'Hisse candle güncelleme başlatıldı (Bu işlem 10-15 dakika sürebilir)') },
        { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + 5y candles, 15-20 dakika)', handler: () => handleTrigger('full', adminService.triggerStockFull, 'Hisse tam güncelleme başlatıldı (Bu işlem 15-20 dakika sürebilir)') },
    ];

    if (loading && stocks.length === 0) return <LoadingState message="Hisse verileri yükleniyor…" />;
    if (error) return <ErrorState message="Hisse senedi verileri yüklenirken hata oluştu" onRetry={refetch} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Activity className="h-5 w-5" />}
                title="Borsa İstanbul (BIST)"
                onRefresh={refetch}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

            <div className="flex flex-wrap items-center gap-3">
                <div className="w-48">
                    <SearchInput value={listParams.search} onChange={listParams.setSearch} placeholder="Hisse ara..." withSuggestions filterType="STOCK" />
                </div>
                {totalElements > 0 && (
                    <span className="text-xs text-fg-muted">{totalElements} hisse</span>
                )}
                <SortSelect
                    value={listParams.sort}
                    direction={listParams.direction}
                    options={SORT_OPTIONS}
                    onSortChange={listParams.setSort}
                    onDirectionChange={listParams.setDirection}
                />
                <FilterTabs
                    items={tabItems}
                    activeId={segment}
                    onSelect={handleSegmentChange}
                    showAll={false}
                    layoutId="stock-segment"
                />
            </div>

            {indices.length > 0 && (
                <motion.div
                    variants={containerVariants()}
                    initial="hidden"
                    animate="show"
                    className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
                >
                    {indices.map((index) => {
                        const cls = getChangeClass(index.changePercent);
                        return (
                            <motion.div
                                key={index.code}
                                variants={cardVariants}
                                onClick={() => navigate(`/stocks/${index.code}`)}
                                className="group cursor-pointer rounded-2xl border border-border-default bg-bg-elevated p-5 card-hover transition-all duration-200 hover:border-border-hover"
                            >
                                <div>
                                    <h3 className="text-base font-semibold text-fg">{index.name || index.code.replace('.IS', '')}</h3>
                                    <span className="text-xs text-fg-muted">Endeks</span>
                                </div>
                                <p className="mt-3 font-mono text-2xl font-bold text-fg">{formatStockPrice(index.price)}</p>
                                <div className={`mt-2 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                    {index.changePercent > 0 ? <ChevronUp className="h-3.5 w-3.5" /> : index.changePercent < 0 ? <ChevronDown className="h-3.5 w-3.5" /> : null}
                                    {Math.abs(index.changePercent || 0).toFixed(2)}%
                                </div>
                            </motion.div>
                        );
                    })}
                </motion.div>
            )}

            {stocks.length > 0 && (
                <motion.div
                    variants={containerVariants()}
                    initial="hidden"
                    animate="show"
                    className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
                >
                    {stocks.map((stock) => {
                        const cls = getChangeClass(stock.changePercent);
                        return (
                            <motion.div
                                key={stock.code}
                                variants={cardVariants}
                                onClick={() => navigate(`/stocks/${stock.code}`)}
                                className="group cursor-pointer rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                            >
                                <div className="flex items-start justify-between">
                                    <div className="min-w-0 flex-1">
                                        <h3 className="truncate text-sm font-semibold text-fg">{assetCodeLabel('STOCK', stock.code)}</h3>
                                        <span className="block truncate text-xs text-fg-muted">{stock.name}</span>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <span className="rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
                                            {stock.metadata?.exchange || 'BIST'}
                                        </span>
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                setBuyTarget({ assetCode: stock.code, assetName: stock.name || stock.code, price: stock.price });
                                            }}
                                            title="Satın Al"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                        >
                                            <ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />
                                        </button>
                                    </div>
                                </div>

                                <div className="mt-3">
                                    <p className="font-mono text-xl font-bold text-fg">₺{formatStockPrice(stock.price)}</p>
                                    <div className={`mt-1 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                        {stock.changePercent > 0 ? <TrendingUp className="h-3.5 w-3.5" /> : stock.changePercent < 0 ? <TrendingDown className="h-3.5 w-3.5" /> : null}
                                        {Math.abs(stock.changePercent || 0).toFixed(2)}%
                                        <span className="ml-1 opacity-75">({stock.changeAmount > 0 ? '+' : ''}₺{formatStockPrice(stock.changeAmount)})</span>
                                    </div>
                                </div>

                                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                                    {stock.metadata?.openPrice != null && (
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="text-fg-muted">Açılış</span>
                                            <span className="font-mono text-fg">₺{formatStockPrice(stock.metadata.openPrice)}</span>
                                        </div>
                                    )}
                                    {stock.metadata?.dayHigh != null && (
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="flex items-center gap-1 text-fg-muted"><ChevronUp className="h-3 w-3 text-success" />En Yüksek</span>
                                            <span className="font-mono text-fg">₺{formatStockPrice(stock.metadata.dayHigh)}</span>
                                        </div>
                                    )}
                                    {stock.metadata?.dayLow != null && (
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="flex items-center gap-1 text-fg-muted"><ChevronDown className="h-3 w-3 text-danger" />En Düşük</span>
                                            <span className="font-mono text-fg">₺{formatStockPrice(stock.metadata.dayLow)}</span>
                                        </div>
                                    )}
                                    <div className="flex items-center justify-between text-xs">
                                        <span className="text-fg-muted">Hacim</span>
                                        <span className="font-mono text-fg">{formatVolume(stock.metadata?.volume)}</span>
                                    </div>
                                </div>

                                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                                    <Clock className="h-3 w-3" />
                                    {stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                                </div>
                            </motion.div>
                        );
                    })}
                </motion.div>
            )}

            {stocks.length === 0 && !loading && (
                <EmptyState
                    icon={<BarChart2 className="h-7 w-7 text-fg-subtle" />}
                    message={listParams.search ? 'Aramayla eşleşen hisse bulunamadı.' : 'Henüz hisse senedi verisi yok.'}
                    hint={!listParams.search && isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : undefined}
                />
            )}

            <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />

            {buyTarget && (
                <BuyModal
                    assetType="STOCK"
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
export default StocksPage;
