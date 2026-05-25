import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Bitcoin, BarChart2, Activity, Clock } from 'lucide-react';
import { TrendingUp, ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { cryptoService } from './services/cryptoService';
import { adminService } from '../admin/services/adminService';
import { formatCompactNumber } from '../../shared/utils/formatters';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/asset/ChangePercentBadge';
import useListParams from '../../shared/hooks/useListParams';
import { useMoney } from '../../shared/hooks/useMoney';

const SORT_OPTION_IDS = ['changePercent', 'price', 'name'];

export default function CryptoPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const listParams = useListParams();
    const { format: money, currency: displayCurrency } = useMoney();
    const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`market.sort.${id}`) }));

    const renderCard = (crypto, { setBuyTarget }) => {
        const symbol = crypto.metadata?.symbol;
        const priceUsd = crypto.metadata?.currentPriceUsd;
        return (
            <AssetCard
                key={crypto.code}
                onClick={() => navigate(`/crypto/${crypto.code}`)}
                className="overflow-hidden relative"
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
                            <span className="block text-xs text-fg-muted leading-snug line-clamp-2 break-words">{crypto.name}</span>
                        </div>
                    </div>
                    <AssetBuyButton
                        onClick={() => setBuyTarget({ assetCode: crypto.code, assetName: `${symbol} - ${crypto.name}`, price: crypto.price })}
                    />
                </div>

                <div className="mt-3 space-y-1">
                    <span className="font-mono text-xl font-bold text-fg">{money(priceUsd, 'USD')}</span>
                    {displayCurrency !== 'USD' && (
                        <div className="flex items-center gap-2 text-xs text-fg-muted">
                            <span className="font-mono">{money(crypto.price)}</span>
                        </div>
                    )}
                </div>

                <ChangePercentBadge
                    value={crypto.changePercent}
                    positiveIcon={<ArrowUpRight className="h-3.5 w-3.5" />}
                    negativeIcon={<ArrowDownRight className="h-3.5 w-3.5" />}
                    size="sm"
                    className="mt-2"
                >
                    <span className="ml-1 opacity-75">{t('common.period24h')}</span>
                </ChangePercentBadge>

                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><Activity className="h-3 w-3" />{t('market.crypto.changeLabel')}</span>
                        <span className="font-mono text-fg">{money(crypto.changeAmount, 'USD')}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><BarChart2 className="h-3 w-3" />{t('market.crypto.volumeLabel')}</span>
                        <span className="font-mono text-fg">{formatCompactNumber(crypto.metadata?.totalVolume)}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><TrendingUp className="h-3 w-3" />{t('market.crypto.marketCapLabel')}</span>
                        <span className="font-mono text-fg">{formatCompactNumber(crypto.metadata?.marketCap)}</span>
                    </div>
                </div>

                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {crypto.lastUpdated ? new Date(crypto.lastUpdated).toLocaleString(t('common.localeTag'), { timeZone: 'Europe/Istanbul' }) : '—'}
                </div>
            </AssetCard>
        );
    };

    return (
        <MarketListPage
            title={t('market.crypto.title')}
            icon={<Bitcoin className="h-5 w-5" />}
            emptyIcon={<Bitcoin className="h-7 w-7 text-fg-subtle" />}
            marketType="CRYPTO"
            service={cryptoService}
            queryKey="cryptos"
            listParams={listParams}
            searchPlaceholder={t('market.crypto.searchPlaceholder')}
            countLabel={t('market.crypto.countLabel')}
            sortOptions={sortOptions}
            adminTriggers={[
                { key: 'snapshot', label: t('market.admin.snapshot'), title: t('market.admin.snapshotTitle'), fn: adminService.triggerCryptoSnapshot, refetchDelay: 5000 },
                { key: 'candles', label: t('market.admin.candles'), title: t('market.admin.candlesTitle'), fn: adminService.triggerCryptoCandles },
                { key: 'full', label: t('market.admin.full'), title: t('market.admin.fullTitle'), fn: adminService.triggerCryptoFull, refetchDelay: 5000 },
            ]}
            renderCard={renderCard}
            loadingMessage={t('market.crypto.loading')}
            errorMessage={t('market.crypto.error')}
            emptyMessage={t('market.crypto.empty')}
            emptyHint={t('market.empty.adminHint')}
            gridClass="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
            animatePresence
        />
    );
}
