import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Activity, Clock, Coins } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/feedback/AnimatedIcons';
import { forexService } from './services/forexService';
import { adminService } from '../admin/services/adminService';
import { getBaseCurrency } from '../../shared/constants/forex';
import { changeColors, changeBg, formatChange, formatPercent } from '../../shared/utils/formatters';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import useListParams from '../../shared/hooks/useListParams';
import { useMoney } from '../../shared/hooks/useMoney';

const SORT_OPTION_IDS = ['changePercent', 'price', 'name'];

function ForexPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const listParams = useListParams();
    const { format: money } = useMoney();
    const formatForexPrice = (price) => money(price, 'TRY', { minDecimals: 4, maxDecimals: 4 });
    const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`market.sort.${id}`) }));
    const localeTag = t('common.localeTag');
    const dateOptions = { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' };

    const renderCard = (forex, { cls, setBuyTarget }) => {
        const meta = forex.metadata || {};
        const sellingPrice = meta.sellingPrice;
        const buyingPrice = meta.buyingPrice;
        const effectiveBuyingPrice = meta.effectiveBuyingPrice;
        const effectiveSellingPrice = meta.effectiveSellingPrice;
        return (
            <AssetCard
                key={forex.code}
                onClick={() => navigate(`/forex/${forex.code}`)}
                className="overflow-hidden relative"
            >
                <div className="flex items-start justify-between">
                    <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                            {forex.image
                                ? (/^https?:\/\//i.test(forex.image)
                                    ? <img src={forex.image} alt={forex.code} className="h-4 w-6 rounded-sm object-cover" />
                                    : <span className="text-lg leading-none">{forex.image}</span>)
                                : <span className="text-lg">💱</span>}
                            <h3 className="truncate text-sm font-semibold text-fg">
                                {getBaseCurrency(forex.code)} / TRY
                            </h3>
                        </div>
                        <span className="mt-0.5 block text-xs text-fg-muted leading-snug line-clamp-2 break-words">
                            {forex.name}
                        </span>
                    </div>
                    <AssetBuyButton
                        onClick={() => setBuyTarget({ assetCode: forex.code, assetName: forex.name, price: sellingPrice ?? forex.price })}
                    />
                </div>

                <div className="mt-3 space-y-1">
                    <div className="flex items-center justify-between">
                        <span className="text-xs text-fg-muted">{t('market.forex.sellLabel')}</span>
                        <span className="font-mono text-xl font-bold text-fg">{formatForexPrice(sellingPrice ?? forex.price)}</span>
                    </div>
                    {buyingPrice != null && (
                        <div className="flex items-center justify-between">
                            <span className="text-xs text-fg-muted">{t('market.forex.buyLabel')}</span>
                            <span className="font-mono text-base font-semibold text-fg-muted">{formatForexPrice(buyingPrice)}</span>
                        </div>
                    )}
                </div>

                {(forex.changeAmount != null && forex.changePercent != null) && (
                    <div className={`mt-2 inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                        {forex.changeAmount > 0 ? <ArrowUpRight className="h-3.5 w-3.5" /> : forex.changeAmount < 0 ? <ArrowDownRight className="h-3.5 w-3.5" /> : null}
                        <span>{formatChange(forex.changeAmount)}</span>
                        <span className="opacity-75">({formatPercent(forex.changePercent)})</span>
                    </div>
                )}

                {(effectiveBuyingPrice != null || effectiveSellingPrice != null) && (
                    <div className="mt-3 space-y-1.5 border-t border-border-default pt-3">
                        <h4 className="flex items-center gap-1.5 text-xs font-medium text-fg-muted">
                            <Activity className="h-3 w-3 text-fg-subtle" />
                            {t('market.forex.banknoteRates')}
                        </h4>
                        {effectiveBuyingPrice != null && (
                            <div className="flex items-center justify-between text-xs">
                                <span className="text-fg-muted">{t('market.forex.banknoteBuy')}</span>
                                <span className="font-mono text-fg">{formatForexPrice(effectiveBuyingPrice)}</span>
                            </div>
                        )}
                        {effectiveSellingPrice != null && (
                            <div className="flex items-center justify-between text-xs">
                                <span className="text-fg-muted">{t('market.forex.banknoteSell')}</span>
                                <span className="font-mono text-fg">{formatForexPrice(effectiveSellingPrice)}</span>
                            </div>
                        )}
                    </div>
                )}

                {forex.lastUpdated && (
                    <div className="mt-3 flex items-center gap-3 text-[11px] text-fg-subtle">
                        <span className="flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {new Date(forex.lastUpdated).toLocaleString(localeTag, dateOptions)}
                        </span>
                    </div>
                )}
            </AssetCard>
        );
    };

    return (
        <MarketListPage
            title={t('market.forex.title')}
            icon={<Coins className="h-5 w-5" />}
            emptyIcon={<Coins className="h-8 w-8 text-fg-subtle" />}
            marketType="FOREX"
            service={forexService}
            queryKey="forex"
            listParams={listParams}
            searchPlaceholder={t('market.forex.searchPlaceholder')}
            countLabel={t('market.forex.countLabel')}
            sortOptions={sortOptions}
            adminTriggers={[
                { key: 'snapshot', label: t('market.admin.snapshot'), title: t('market.admin.snapshotTitle'), fn: adminService.triggerForexSnapshot, successMsg: t('market.admin.snapshotStarted'), refetchDelay: 5000 },
                { key: 'candles', label: t('market.admin.candles'), title: t('market.admin.candlesTitle'), fn: adminService.triggerForexCandles, successMsg: t('market.admin.candlesStarted') },
                { key: 'full', label: t('market.admin.full'), title: t('market.admin.fullTitle'), fn: adminService.triggerForexFull, successMsg: t('market.admin.fullStarted'), refetchDelay: 5000 },
            ]}
            renderCard={renderCard}
            loadingMessage={t('market.forex.loading')}
            errorMessage={t('market.forex.error')}
            emptyMessage={t('market.forex.empty')}
            emptyHint={t('market.empty.adminHint')}
            gridClass="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
            animatePresence
        />
    );
}
export default ForexPage;
