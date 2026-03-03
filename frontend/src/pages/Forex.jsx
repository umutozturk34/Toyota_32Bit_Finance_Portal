import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
    DollarSign,
    BarChart2,
    Activity,
    Clock,
    ArrowUpRight,
    ArrowDownRight,
    Coins,
} from 'lucide-react';
import { forexService, adminService } from '../services/marketService';
import { getForexPairs, getForexDisplayName, getForexFlag, getBaseCurrency } from '../constants/forex';
import { useAuth } from '../context/AuthContext';
import { getChangeClass, changeColors, changeBg, formatPrice, formatChange, formatPercent } from '../utils/formatters';
import { containerVariants, cardVariants } from '../utils/animations';
import LoadingState from '../components/LoadingState';
import ErrorState from '../components/ErrorState';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
function Forex() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [forexData, setForexData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
        tcmb: false,
    });
    const isAdmin = hasRole('ADMIN');
    console.log('[Forex] isAdmin:', isAdmin);
    useEffect(() => {
        console.log('[Forex] useEffect - fetching forex data');
        fetchForexData();
    }, []);
    const fetchForexData = async () => {
        console.log('[Forex] fetchForexData() called');
        setLoading(true);
        setError(null);
        try {
            const pairs = getForexPairs();
            console.log('[Forex] Fetching forex for pairs:', pairs);
            const data = await forexService.getMultipleForex(pairs);
            console.log('[Forex] fetchForexData() success, data:', data);
            setForexData(data || []);
        } catch (err) {
            console.error('[Forex] fetchForexData() error:', err);
            setError('Döviz kuru verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };
    const handleForexSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerForexSnapshot();
            alert(response.message || 'TCMB + Yahoo snapshot güncelleme başlatıldı (~1 dakika, 21 forex × 2sn)');
            setTimeout(fetchForexData, 5000);
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };
    const handleForexCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerForexCandles();
            alert(response.message || 'Yahoo Finance candles güncelleme başlatıldı (~10 dakika, 20 forex × 5y OHLC)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };
    const handleForexFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerForexFull();
            alert(response.message || 'Yahoo Finance FULL güncelleme başlatıldı (~12 dakika, snapshot + 5y candles)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, full: false }));
        }
    };
    const formatForexPrice = (price) => formatPrice(price, { locale: 'tr-TR', minDecimals: 4, maxDecimals: 4 });

    const handleCardClick = (currencyCode) => {
        navigate(`/charts?type=FOREX&symbol=${currencyCode}&range=3M`);
    };

    const adminActions = [
        { key: 'snapshot', label: 'Snapshot', title: 'TCMB + Yahoo snapshot güncelle (~1 dakika, 21 forex × 2sn)', handler: handleForexSnapshotUpdate },
        { key: 'candles', label: 'Candles (5y)', title: 'Yahoo Finance candles güncelle (~10 dakika, 20 forex × 5y OHLC)', handler: handleForexCandlesUpdate },
        { key: 'full', label: 'Full Update', title: 'Yahoo Finance FULL update (~12 dakika, snapshot + 5y candles)', handler: handleForexFullUpdate },
    ];
    if (loading) return <LoadingState message="Döviz kurları yükleniyor…" />;
    if (error) return <ErrorState message={error} onRetry={fetchForexData} />;
        return (
        <div className="space-y-6 py-6">
            {}
            <PageHeader
                icon={<Coins className="h-5 w-5" />}
                title="Döviz Kurları"
                onRefresh={fetchForexData}
                loading={loading}
                isAdmin={isAdmin}
                adminActions={adminActions}
                updating={updating}
            />
            {}
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
                        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                    >
                        {[...forexData].sort((a, b) => {
                            const timeA = a.yahooUpdatedAt ? new Date(a.yahooUpdatedAt).getTime() : 0;
                            const timeB = b.yahooUpdatedAt ? new Date(b.yahooUpdatedAt).getTime() : 0;
                            return timeA - timeB;
                        }).map((forex) => {
                            const cls = getChangeClass(forex.change24h);
                            return (
                                <motion.div
                                    key={forex.currencyCode}
                                    variants={cardVariants}
                                    onClick={() => handleCardClick(forex.currencyCode)}
                                    className="group cursor-pointer rounded-xl border border-border-default bg-bg-elevated p-4 card-hover transition-all duration-200 hover:border-border-hover"
                                >
                                    {}
                                    <div className="flex items-start justify-between">
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2">
                                                <span className="text-lg">{getForexFlag(forex.currencyCode)}</span>
                                                <h3 className="truncate text-sm font-semibold text-fg">
                                                    {getBaseCurrency(forex.currencyCode)} / TRY
                                                </h3>
                                            </div>
                                            <span className="mt-0.5 block truncate text-xs text-fg-muted">
                                                {getForexDisplayName(forex.currencyCode)}
                                            </span>
                                        </div>
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handleCardClick(forex.currencyCode);
                                            }}
                                            title="Grafiği Görüntüle"
                                            className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle transition-colors duration-150 hover:bg-surface hover:text-accent"
                                        >
                                            <BarChart2 className="h-3.5 w-3.5" />
                                        </button>
                                    </div>
                                    {}
                                    {isAdmin && forex.updatedAt && (
                                        <div className="mt-2 flex items-center justify-between rounded-md bg-surface px-2.5 py-1.5 text-[10px] text-fg-subtle">
                                            <span className="flex items-center gap-1">
                                                <BarChart2 className="h-2.5 w-2.5" />
                                                Yahoo: {forex.yahooUpdatedAt ?
                                                    new Date(forex.yahooUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                                    'N/A'
                                                }
                                            </span>
                                            <span className="flex items-center gap-1">
                                                <Activity className="h-2.5 w-2.5" />
                                                TCMB: {forex.tcmbUpdatedAt ?
                                                    new Date(forex.tcmbUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                                    'N/A'
                                                }
                                            </span>
                                        </div>
                                    )}
                                    {}
                                    <div className="mt-3 space-y-1">
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs text-fg-muted">Alış:</span>
                                            <span className="font-mono text-xl font-bold text-fg">₺ {formatForexPrice(forex.currentPrice)}</span>
                                        </div>
                                        {forex.sellingPrice && (
                                            <div className="flex items-center justify-between">
                                                <span className="text-xs text-fg-muted">Satış:</span>
                                                <span className="font-mono text-base font-semibold text-fg-muted">₺ {formatForexPrice(forex.sellingPrice)}</span>
                                            </div>
                                        )}
                                    </div>
                                    {}
                                    {(forex.change24h !== null && forex.change24h !== undefined &&
                                        forex.changePercent24h !== null && forex.changePercent24h !== undefined) && (
                                            <div className={`mt-2 inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                                                {forex.change24h > 0 ? (
                                                    <ArrowUpRight className="h-3.5 w-3.5" />
                                                ) : forex.change24h < 0 ? (
                                                    <ArrowDownRight className="h-3.5 w-3.5" />
                                                ) : null}
                                                <span>{formatChange(forex.change24h)} TRY</span>
                                                <span className="opacity-75">({formatPercent(forex.changePercent24h)})</span>
                                            </div>
                                        )}
                                    {}
                                    {(forex.forexBuying || forex.forexSelling) && (
                                        <div className="mt-3 space-y-1.5 border-t border-border-default pt-3">
                                            <h4 className="flex items-center gap-1.5 text-xs font-medium text-fg-muted">
                                                <Activity className="h-3 w-3 text-fg-subtle" />
                                                TCMB Kurları
                                            </h4>
                                            {forex.forexBuying && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Döviz Alış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(forex.forexBuying)}</span>
                                                </div>
                                            )}
                                            {forex.forexSelling && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Döviz Satış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(forex.forexSelling)}</span>
                                                </div>
                                            )}
                                            {forex.banknoteBuying && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Efektif Alış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(forex.banknoteBuying)}</span>
                                                </div>
                                            )}
                                            {forex.banknoteSelling && (
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-fg-muted">Efektif Satış:</span>
                                                    <span className="font-mono text-fg">₺ {formatForexPrice(forex.banknoteSelling)}</span>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    {}
                                    {!isAdmin && forex.updatedAt && (
                                        <div className="mt-3 flex items-center gap-1 text-[11px] text-fg-subtle">
                                            <Clock className="h-3 w-3" />
                                            Son Güncelleme: {(() => {
                                                const tcmbTime = forex.tcmbUpdatedAt ? new Date(forex.tcmbUpdatedAt) : null;
                                                const yahooTime = forex.yahooUpdatedAt ? new Date(forex.yahooUpdatedAt) : null;
                                                const lastUpdate = tcmbTime && yahooTime ?
                                                    (tcmbTime > yahooTime ? tcmbTime : yahooTime) :
                                                    (tcmbTime || yahooTime || new Date(forex.updatedAt));
                                                return lastUpdate.toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
                                            })()}
                                        </div>
                                    )}
                                </motion.div>
                            );
                        })}
                    </motion.div>
                </motion.section>
            )}
            {}
            {forexData.length === 0 && (
                <EmptyState
                    icon={<Coins className="h-8 w-8 text-fg-subtle" />}
                    message="Henüz döviz kuru verisi bulunmuyor."
                    hint={isAdmin ? 'Admin butonlarını kullanarak veri çekebilirsiniz.' : 'Admin veri güncellemesini bekleyin.'}
                />
            )}
        </div>
    );
}
export default Forex;
