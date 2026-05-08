import { useNavigate } from 'react-router-dom';
import { BarChart2, Activity, Clock, Coins } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { forexService } from './forexService';
import { adminService } from '../admin/services/adminService';
import { getForexFlag, getBaseCurrency } from '../../shared/constants/forex';
import { useAuth } from '../auth/AuthContext';
import { changeColors, changeBg, formatPrice, formatChange, formatPercent } from '../../shared/utils/formatters';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import useListParams from '../../shared/hooks/useListParams';

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

const formatForexPrice = (price) => formatPrice(price, { locale: 'tr-TR', minDecimals: 4, maxDecimals: 4 });

function ForexPage() {
    const navigate = useNavigate();
    const listParams = useListParams();
    const { hasRole } = useAuth();
    const isAdmin = hasRole('ADMIN');

    const renderCard = (forex, { cls, setBuyTarget }) => {
        const meta = forex.metadata || {};
        const sellingPrice = meta.sellingPrice;
        return (
            <AssetCard
                key={forex.code}
                onClick={() => navigate(`/forex/${forex.code}`)}
                className="overflow-hidden relative"
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
                    <AssetBuyButton
                        onClick={() => setBuyTarget({ assetCode: forex.code, assetName: forex.name, price: sellingPrice ?? forex.price })}
                    />
                </div>

                {isAdmin && forex.lastUpdated && (
                    <div className="mt-2 flex items-center justify-between rounded-md bg-surface px-2.5 py-1.5 text-[10px] text-fg-subtle">
                        <span className="flex items-center gap-1">
                            <BarChart2 className="h-2.5 w-2.5" />
                            Yahoo: {meta.yahooUpdatedAt ? new Date(meta.yahooUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'N/A'}
                        </span>
                        <span className="flex items-center gap-1">
                            <Activity className="h-2.5 w-2.5" />
                            TCMB: {meta.tcmbUpdatedAt ? new Date(meta.tcmbUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'N/A'}
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
                        {forex.changeAmount > 0 ? <ArrowUpRight className="h-3.5 w-3.5" /> : forex.changeAmount < 0 ? <ArrowDownRight className="h-3.5 w-3.5" /> : null}
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
            </AssetCard>
        );
    };

    return (
        <MarketListPage
            title="Döviz Kurları"
            icon={<Coins className="h-5 w-5" />}
            emptyIcon={<Coins className="h-8 w-8 text-fg-subtle" />}
            marketType="FOREX"
            service={forexService}
            queryKey="forex"
            listParams={listParams}
            searchPlaceholder="Döviz ara..."
            countLabel="döviz çifti"
            sortOptions={SORT_OPTIONS}
            adminTriggers={[
                { key: 'snapshot', label: 'Snapshot', title: 'TCMB + Yahoo snapshot güncelle (~1 dakika, 21 forex × 2sn)', fn: adminService.triggerForexSnapshot, successMsg: 'TCMB + Yahoo snapshot güncelleme başlatıldı', refetchDelay: 5000 },
                { key: 'candles', label: 'Candles (5y)', title: 'Yahoo Finance candles güncelle (~10 dakika, 20 forex × 5y OHLC)', fn: adminService.triggerForexCandles, successMsg: 'Yahoo Finance candles güncelleme başlatıldı' },
                { key: 'full', label: 'Full Update', title: 'Yahoo Finance FULL update (~12 dakika, snapshot + 5y candles)', fn: adminService.triggerForexFull, successMsg: 'Yahoo Finance FULL güncelleme başlatıldı', refetchDelay: 5000 },
            ]}
            renderCard={renderCard}
            loadingMessage="Döviz kurları yükleniyor…"
            errorMessage="Döviz kuru verileri yüklenirken hata oluştu"
            emptyMessage="Henüz döviz kuru verisi bulunmuyor."
            emptyHint="Admin butonlarını kullanarak veri çekebilirsiniz."
            gridClass="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
            animatePresence
        />
    );
}
export default ForexPage;
