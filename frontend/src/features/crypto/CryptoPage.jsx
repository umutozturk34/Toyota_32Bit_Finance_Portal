import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Bitcoin, BarChart2, Activity, Clock } from 'lucide-react';
import { TrendingUp, ArrowUpRight, ArrowDownRight, ShoppingCart } from '../../shared/components/AnimatedIcons';
import { cryptoService } from './cryptoService';
import { adminService } from '../admin/adminService';
import { changeColors, changeBg, formatPriceUSD, formatPriceTRY, formatCompactNumber, formatPercentAbs } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import MarketListPage from '../../shared/components/MarketListPage';
import useListParams from '../../shared/hooks/useListParams';

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

export default function CryptoPage() {
    const navigate = useNavigate();
    const listParams = useListParams();

    const renderCard = (crypto, { cls, setBuyTarget }) => {
        const symbol = crypto.metadata?.symbol;
        const priceUsd = crypto.metadata?.currentPriceUsd;
        return (
            <motion.div
                key={crypto.code}
                variants={cardVariants}
                onClick={() => navigate(`/crypto/${crypto.code}`)}
                className="group cursor-pointer rounded-2xl border border-border-default bg-bg-elevated p-5 card-hover transition-all duration-200 hover:border-border-hover overflow-hidden relative"
            >
                <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                        {crypto.image ? (
                            <img src={crypto.image} alt={symbol} className="w-8 h-8 rounded-full" />
                        ) : (
                            <span className="flex items-center justify-center w-8 h-8 rounded-full bg-warning/10 text-xs font-bold text-warning">
                                {symbol?.slice(0, 2)}
                            </span>
                        )}
                        <div className="min-w-0">
                            <h3 className="truncate text-sm font-semibold text-fg">{symbol}</h3>
                            <span className="block truncate text-xs text-fg-muted">{crypto.name}</span>
                        </div>
                    </div>
                    <button
                        onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            setBuyTarget({ assetCode: crypto.code, assetName: `${symbol} - ${crypto.name}`, price: crypto.price });
                        }}
                        title="Satın Al"
                        className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
                    >
                        <ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />
                    </button>
                </div>

                <div className="mt-3 space-y-1">
                    <span className="font-mono text-xl font-bold text-fg">{formatPriceUSD(priceUsd)}</span>
                    <div className="flex items-center gap-2 text-xs text-fg-muted">
                        <span className="font-medium">TRY</span>
                        <span className="font-mono">{formatPriceTRY(crypto.price)}</span>
                    </div>
                </div>

                {crypto.changePercent !== null && crypto.changePercent !== undefined && (
                    <div className={`mt-2 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                        {crypto.changePercent > 0 ? (
                            <ArrowUpRight className="h-3.5 w-3.5" />
                        ) : crypto.changePercent < 0 ? (
                            <ArrowDownRight className="h-3.5 w-3.5" />
                        ) : null}
                        {formatPercentAbs(crypto.changePercent)}
                        <span className="ml-1 opacity-75">24h</span>
                    </div>
                )}

                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><Activity className="h-3 w-3" />Change</span>
                        <span className="font-mono text-fg">{formatPriceUSD(crypto.changeAmount)}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><BarChart2 className="h-3 w-3" />Volume</span>
                        <span className="font-mono text-fg">{formatCompactNumber(crypto.metadata?.totalVolume)}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><TrendingUp className="h-3 w-3" />Market Cap</span>
                        <span className="font-mono text-fg">{formatCompactNumber(crypto.metadata?.marketCap)}</span>
                    </div>
                </div>

                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {crypto.lastUpdated ? new Date(crypto.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                </div>
            </motion.div>
        );
    };

    return (
        <MarketListPage
            title="Kripto Paralar"
            icon={<Bitcoin className="h-5 w-5" />}
            emptyIcon={<Bitcoin className="h-7 w-7 text-fg-subtle" />}
            marketType="CRYPTO"
            service={cryptoService}
            queryKey="cryptos"
            listParams={listParams}
            searchPlaceholder="Kripto ara..."
            countLabel="kripto"
            sortOptions={SORT_OPTIONS}
            adminTriggers={[
                { key: 'snapshot', label: 'Snapshot', title: 'Kripto snapshot verilerini güncelle', fn: adminService.triggerCryptoSnapshot, refetchDelay: 5000 },
                { key: 'candles', label: 'Candles', title: 'Kripto mum verilerini güncelle', fn: adminService.triggerCryptoCandles },
                { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + candles)', fn: adminService.triggerCryptoFull, refetchDelay: 5000 },
            ]}
            renderCard={renderCard}
            loadingMessage="Kripto verileri yükleniyor…"
            errorMessage="Kripto para verileri yüklenirken hata oluştu"
            emptyMessage="Henüz kripto para verisi yok."
            emptyHint="Admin butonlarını kullanarak veri çekebilirsiniz."
            gridClass="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
            animatePresence
        />
    );
}
