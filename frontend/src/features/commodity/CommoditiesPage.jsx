import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { Gem, ChevronUp, ChevronDown, Clock } from 'lucide-react';
import { TrendingUp, TrendingDown, ShoppingCart } from '../../shared/components/AnimatedIcons';
import { commodityService } from './commodityService';
import { adminService } from '../admin/adminService';
import { useAuth } from '../auth/AuthContext';
import { getChangeClass, changeColors, changeBg, formatPrice } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import EmptyState from '../../shared/components/EmptyState';
import PageHeader from '../../shared/components/PageHeader';
import SearchInput from '../../shared/components/SearchInput';
import SortSelect from '../../shared/components/SortSelect';
import Pagination from '../../shared/components/Pagination';
import BuyModal from '../../shared/components/BuyModal';
import { toast } from '../../shared/components/Toast';
import useListParams from '../../shared/hooks/useListParams';
import { commodityDisplayCode, isCommodityIndex } from '../../shared/constants/commodities';

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

function CommoditiesPage() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [buyTarget, setBuyTarget] = useState(null);
    const [updating, setUpdating] = useState({ snapshot: false, candles: false, full: false });
    const isAdmin = hasRole('ADMIN');
    const listParams = useListParams();

    const { data, isLoading: loading, error, refetch } = useQuery({
        queryKey: ['commodities', listParams.params],
        queryFn: () => commodityService.getAllCommodities(listParams.params),
        placeholderData: (prev) => prev,
    });

    const all = data?.content || [];
    const indices = all.filter(c => isCommodityIndex(c.code));
    const futures = all.filter(c => !isCommodityIndex(c.code));
    const totalPages = data?.totalPages || 0;
    const totalElements = data?.totalElements || 0;

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

    const formatCommodityPrice = (price) => formatPrice(price, { locale: 'tr-TR' });

    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'Emtia snapshot verilerini güncelle', handler: () => handleTrigger('snapshot', adminService.triggerCommoditySnapshot, 'Emtia snapshot güncelleme başlatıldı') },
        { key: 'candles', label: 'Candles (5y)', title: '5 yıllık OHLC verilerini güncelle', handler: () => handleTrigger('candles', adminService.triggerCommodityCandles, 'Emtia candle güncelleme başlatıldı') },
        { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + 5y candles)', handler: () => handleTrigger('full', adminService.triggerCommodityFull, 'Emtia tam güncelleme başlatıldı') },
    ];

    if (loading && all.length === 0) return <LoadingState message="Emtia verileri yükleniyor…" />;
    if (error) return <ErrorState message="Emtia verileri yüklenirken hata oluştu" onRetry={refetch} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Gem className="h-5 w-5" />}
                title="Emtia"
                onRefresh={refetch}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

            <div className="flex flex-wrap items-center gap-3">
                <div className="w-48">
                    <SearchInput value={listParams.search} onChange={listParams.setSearch} placeholder="Emtia ara..." withSuggestions filterType="COMMODITY" />
                </div>
                {totalElements > 0 && (
                    <span className="text-xs text-fg-muted">{totalElements} emtia</span>
                )}
                <SortSelect
                    value={listParams.sort}
                    direction={listParams.direction}
                    options={SORT_OPTIONS}
                    onSortChange={listParams.setSort}
                    onDirectionChange={listParams.setDirection}
                />
            </div>

            {indices.length > 0 && (
                <motion.div
                    variants={containerVariants()}
                    initial="hidden"
                    animate="show"
                    className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
                >
                    {indices.map((commodity) => {
                        const cls = getChangeClass(commodity.changePercent);
                        return (
                            <motion.div
                                key={commodity.code}
                                variants={cardVariants}
                                onClick={() => navigate(`/commodities/${encodeURIComponent(commodity.code)}`)}
                                className="group cursor-pointer rounded-2xl border border-border-default bg-bg-elevated p-5 card-hover transition-all duration-200 hover:border-border-hover"
                            >
                                <div>
                                    <h3 className="text-base font-semibold text-fg">{commodity.name || commodity.code}</h3>
                                    <span className="text-xs text-fg-muted">Endeks</span>
                                </div>
                                <p className="mt-3 font-mono text-2xl font-bold text-fg">₺{formatCommodityPrice(commodity.price)}</p>
                                <div className={`mt-2 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                    {commodity.changePercent > 0 ? <ChevronUp className="h-3.5 w-3.5" /> : commodity.changePercent < 0 ? <ChevronDown className="h-3.5 w-3.5" /> : null}
                                    {Math.abs(commodity.changePercent || 0).toFixed(2)}%
                                </div>
                            </motion.div>
                        );
                    })}
                </motion.div>
            )}

            {futures.length > 0 && (
                <motion.div
                    variants={containerVariants()}
                    initial="hidden"
                    animate="show"
                    className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[400px] content-start"
                >
                    {futures.map((commodity) => {
                        const cls = getChangeClass(commodity.changePercent);
                        const meta = commodity.metadata || {};
                        const usd = meta.currentPriceUsd;
                        return (
                            <motion.div
                                key={commodity.code}
                                variants={cardVariants}
                                onClick={() => navigate(`/commodities/${encodeURIComponent(commodity.code)}`)}
                                className="group cursor-pointer rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                            >
                                <div className="flex items-start justify-between">
                                    <div className="min-w-0 flex-1">
                                        <h3 className="truncate text-sm font-semibold text-fg">{commodity.name || commodityDisplayCode(commodity.code)}</h3>
                                        <span className="block truncate text-xs text-fg-muted">{commodityDisplayCode(commodity.code)}</span>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        {meta.unit && (
                                            <span className="rounded-md border border-orange-400/20 bg-orange-400/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-orange-400">
                                                {meta.unit}
                                            </span>
                                        )}
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                setBuyTarget({ assetCode: commodity.code, assetName: commodity.name || commodityDisplayCode(commodity.code), price: commodity.price });
                                            }}
                                            title="Satın Al"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                                        >
                                            <ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />
                                        </button>
                                    </div>
                                </div>

                                <div className="mt-3">
                                    <p className="font-mono text-xl font-bold text-fg">₺{formatCommodityPrice(commodity.price)}</p>
                                    <div className={`mt-1 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                        {commodity.changePercent > 0 ? <TrendingUp className="h-3.5 w-3.5" /> : commodity.changePercent < 0 ? <TrendingDown className="h-3.5 w-3.5" /> : null}
                                        {Math.abs(commodity.changePercent || 0).toFixed(2)}%
                                        <span className="ml-1 opacity-75">({commodity.changeAmount > 0 ? '+' : ''}₺{formatCommodityPrice(commodity.changeAmount)})</span>
                                    </div>
                                </div>

                                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                                    {usd != null && (
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="text-fg-muted">USD Fiyat</span>
                                            <span className="font-mono text-fg">${formatCommodityPrice(usd)}</span>
                                        </div>
                                    )}
                                    {meta.sellingPrice != null && (
                                        <div className="flex items-center justify-between text-xs">
                                            <span className="text-fg-muted">Satış Fiyatı</span>
                                            <span className="font-mono text-fg">₺{formatCommodityPrice(meta.sellingPrice)}</span>
                                        </div>
                                    )}
                                </div>

                                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                                    <Clock className="h-3 w-3" />
                                    {commodity.lastUpdated ? new Date(commodity.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                                </div>
                            </motion.div>
                        );
                    })}
                </motion.div>
            )}

            {all.length === 0 && !loading && (
                <EmptyState
                    icon={<Gem className="h-7 w-7 text-fg-subtle" />}
                    message={listParams.search ? 'Aramayla eşleşen emtia bulunamadı.' : 'Henüz emtia verisi yok.'}
                    hint={!listParams.search && isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : undefined}
                />
            )}

            <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />

            {buyTarget && (
                <BuyModal
                    assetType="COMMODITY"
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

export default CommoditiesPage;
