import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
    DollarSign,
    BarChart2,
    Activity,
    Clock,
    Coins,
} from 'lucide-react';
import { ArrowUpRight, ArrowDownRight, ShoppingCart } from '../../shared/components/AnimatedIcons';
import { forexService } from './forexService';
import { adminService } from '../admin/adminService';
import { getForexFlag, getBaseCurrency } from '../../shared/constants/forex';
import { useAuth } from '../auth/AuthContext';
import { getChangeClass, changeColors, changeBg, formatPrice, formatChange, formatPercent } from '../../shared/utils/formatters';
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
import useMarketListData from '../../shared/hooks/useMarketListData';

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

function ForexPage() {
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

    const { data, isLoading: loading, error, refetch } = useMarketListData(
        'forex', forexService.getAll, listParams.params);

    const forexData = data?.content || [];
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

    const formatForexPrice = (price) => formatPrice(price, { locale: 'tr-TR', minDecimals: 4, maxDecimals: 4 });

    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'TCMB + Yahoo snapshot güncelle (~1 dakika, 21 forex × 2sn)', handler: () => handleTrigger('snapshot', adminService.triggerForexSnapshot, 'TCMB + Yahoo snapshot güncelleme başlatıldı') },
        { key: 'candles', label: 'Candles (5y)', title: 'Yahoo Finance candles güncelle (~10 dakika, 20 forex × 5y OHLC)', handler: () => handleTrigger('candles', adminService.triggerForexCandles, 'Yahoo Finance candles güncelleme başlatıldı') },
        { key: 'full', label: 'Full Update', title: 'Yahoo Finance FULL update (~12 dakika, snapshot + 5y candles)', handler: () => handleTrigger('full', adminService.triggerForexFull, 'Yahoo Finance FULL güncelleme başlatıldı') },
    ];

    if (loading && forexData.length === 0) return <LoadingState message="Döviz kurları yükleniyor…" />;
    if (error) return <ErrorState message="Döviz kuru verileri yüklenirken hata oluştu" onRetry={refetch} />;

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Coins className="h-5 w-5" />}
                title="Döviz Kurları"
                onRefresh={refetch}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />

            <div className="flex flex-wrap items-center gap-3">
                <div className="w-48">
                    <SearchInput value={listParams.search} onChange={listParams.setSearch} placeholder="Döviz ara..." withSuggestions filterType="FOREX" />
                </div>
                {totalElements > 0 && (
                    <span className="text-xs text-fg-muted">{totalElements} döviz çifti</span>
                )}
                <SortSelect
                    value={listParams.sort}
                    direction={listParams.direction}
                    options={SORT_OPTIONS}
                    onSortChange={listParams.setSort}
                    onDirectionChange={listParams.setDirection}
                />
            </div>

            <AnimatePresence>
            {forexData.length > 0 && (
                <motion.section
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: 0.1 }}
                    className="space-y-4"
                >
                    <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
                        <DollarSign className="h-5 w-5 text-fg-subtle" />
                        Döviz Çiftleri
                        <span className="ml-1 text-sm font-normal text-fg-muted">({forexData.length} çift)</span>
                    </h2>
                    <motion.div
                        variants={containerVariants()}
                        initial="hidden"
                        animate="show"
                        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
                    >
                        {forexData.map((forex) => {
                            const meta = forex.metadata || {};
                            const cls = getChangeClass(forex.changeAmount);
                            const sellingPrice = meta.sellingPrice;
                            return (
                                <motion.div
                                    key={forex.code}
                                    variants={cardVariants}
                                    onClick={() => navigate(`/forex/${forex.code}`)}
                                    className="group cursor-pointer rounded-2xl border border-border-default bg-bg-elevated p-5 card-hover transition-all duration-200 hover:border-border-hover overflow-hidden relative"
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2">
                                                <span className="text-lg">{getForexFlag(forex.code)}</span>
                                                <h3 className="truncate text-sm font-semibold text-fg">
                                                    {getBaseCurrency(forex.code)} / TRY
                                                </h3>
                                            </div>
                                            <span className="mt-0.5 block truncate text-xs text-fg-muted">
                                                {forex.name}
                                            </span>
                                        </div>
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                setBuyTarget({ assetCode: forex.code, assetName: forex.name, price: sellingPrice ?? forex.price });
                                            }}
                                            title="Satın Al"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle transition-colors duration-150 hover:bg-surface hover:text-accent"
                                        >
                                            <ShoppingCart className="h-3.5 w-3.5" />
                                        </button>
                                    </div>

                                    {isAdmin && forex.lastUpdated && (
                                        <div className="mt-2 flex items-center justify-between rounded-md bg-surface px-2.5 py-1.5 text-[10px] text-fg-subtle">
                                            <span className="flex items-center gap-1">
                                                <BarChart2 className="h-2.5 w-2.5" />
                                                Yahoo: {meta.yahooUpdatedAt ?
                                                    new Date(meta.yahooUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                                    'N/A'
                                                }
                                            </span>
                                            <span className="flex items-center gap-1">
                                                <Activity className="h-2.5 w-2.5" />
                                                TCMB: {meta.tcmbUpdatedAt ?
                                                    new Date(meta.tcmbUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                                    'N/A'
                                                }
                                            </span>
                                        </div>
                                    )}

                                    <div className="mt-3 space-y-1">
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs text-fg-muted">Alış:</span>
                                            <span className="font-mono text-xl font-bold text-fg">₺ {formatForexPrice(sellingPrice ?? forex.price)}</span>
                                        </div>
                                        {forex.price && (
                                            <div className="flex items-center justify-between">
                                                <span className="text-xs text-fg-muted">Satış:</span>
                                                <span className="font-mono text-base font-semibold text-fg-muted">₺ {formatForexPrice(forex.price)}</span>
                                            </div>
                                        )}
                                    </div>

                                    {(forex.changeAmount !== null && forex.changeAmount !== undefined &&
                                        forex.changePercent !== null && forex.changePercent !== undefined) && (
                                            <div className={`mt-2 inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                                {forex.changeAmount > 0 ? (
                                                    <ArrowUpRight className="h-3.5 w-3.5" />
                                                ) : forex.changeAmount < 0 ? (
                                                    <ArrowDownRight className="h-3.5 w-3.5" />
                                                ) : null}
                                                <span>{formatChange(forex.changeAmount)} TRY</span>
                                                <span className="opacity-75">({formatPercent(forex.changePercent)})</span>
                                            </div>
                                        )}

                                    {(meta.forexBuying || meta.forexSelling) && (
                                        <div className="mt-3 space-y-1.5 border-t border-border-default pt-3">
                                            <h4 className="flex items-center gap-1.5 text-xs font-medium text-fg-muted">
                                                <Activity className="h-3 w-3 text-fg-subtle" />
                                                TCMB Kurları
                                            </h4>
                                            {meta.forexBuying && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Döviz Alış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(meta.forexBuying)}</span>
                                                </div>
                                            )}
                                            {meta.forexSelling && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Döviz Satış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(meta.forexSelling)}</span>
                                                </div>
                                            )}
                                            {meta.banknoteBuying && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Efektif Alış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(meta.banknoteBuying)}</span>
                                                </div>
                                            )}
                                            {meta.banknoteSelling && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Efektif Satış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(meta.banknoteSelling)}</span>
                                                </div>
                                            )}
                                        </div>
                                    )}

                                    <div className="mt-3 flex items-center gap-3 text-[11px] text-fg-subtle">
                                        {meta.tcmbUpdatedAt && (
                                            <span className="flex items-center gap-1">
                                                <Clock className="h-3 w-3" />
                                                TCMB: {new Date(meta.tcmbUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                                            </span>
                                        )}
                                        {isAdmin && meta.yahooUpdatedAt && (
                                            <span className="flex items-center gap-1">
                                                <Clock className="h-3 w-3" />
                                                Yahoo: {new Date(meta.yahooUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                                            </span>
                                        )}
                                        {!meta.tcmbUpdatedAt && !meta.yahooUpdatedAt && forex.lastUpdated && (
                                            <span className="flex items-center gap-1">
                                                <Clock className="h-3 w-3" />
                                                {new Date(forex.lastUpdated).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                                            </span>
                                        )}
                                    </div>
                                </motion.div>
                            );
                        })}
                    </motion.div>
                </motion.section>
            )}
            </AnimatePresence>

            {forexData.length === 0 && !loading && (
                <EmptyState
                    icon={<Coins className="h-8 w-8 text-fg-subtle" />}
                    message={listParams.search ? 'Aramayla eşleşen döviz bulunamadı.' : 'Henüz döviz kuru verisi bulunmuyor.'}
                    hint={!listParams.search && isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : undefined}
                />
            )}

            <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />

            {buyTarget && (
                <BuyModal
                    assetType="FOREX"
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
export default ForexPage;
