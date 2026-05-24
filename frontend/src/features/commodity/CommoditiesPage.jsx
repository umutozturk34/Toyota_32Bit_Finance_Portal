import { useQuery } from '@tanstack/react-query';
import { STALE } from '../../shared/constants/query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Gem, ChevronUp, ChevronDown, Clock } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../shared/components/feedback/AnimatedIcons';
import { commodityService } from './services/commodityService';
import { adminService } from '../admin/services/adminService';
import { commodityName } from '../../shared/utils/commodityName';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/asset/ChangePercentBadge';
import useListParams from '../../shared/hooks/useListParams';
import { useMoney } from '../../shared/hooks/useMoney';

const SORT_OPTION_IDS = ['changePercent', 'price', 'name'];
const SEGMENT_ORDER = ['PRECIOUS_METAL', 'OTHER'];

function CommoditiesPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const listParams = useListParams();
    const { format: money } = useMoney();
    const segment = listParams.filter || 'ALL';
    const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`market.sort.${id}`) }));
    const segmentLabel = (id) => t(`market.commodity.segments.${id}`);
    const localeTag = t('common.localeTag');

    const { data: segmentCounts = [] } = useQuery({
        queryKey: ['commoditySegments'],
        queryFn: commodityService.getGroupCounts,
        staleTime: STALE.MEDIUM,
    });

    const tabItems = [...segmentCounts]
        .sort((a, b) => SEGMENT_ORDER.indexOf(a.type) - SEGMENT_ORDER.indexOf(b.type))
        .map(s => ({ type: s.type, count: s.count, label: segmentLabel(s.type) }));

    const renderCard = (commodity, { setBuyTarget }) => {
        const meta = commodity.metadata || {};
        const usd = meta.currentPriceUsd;
        const displayName = commodityName(t, commodity.code, commodity.name);
        return (
            <AssetCard
                key={commodity.code}
                onClick={() => navigate(`/commodities/${encodeURIComponent(commodity.code)}`)}
                size="sm"
            >
                <div className="flex items-start justify-between">
                    <div className="min-w-0 flex-1">
                        <h3 className="text-sm font-semibold text-fg leading-snug line-clamp-2 break-words">{displayName}</h3>
                        <span className="block truncate text-xs text-fg-muted">{commodity.code}</span>
                    </div>
                    <div className="flex items-center gap-2">
                        {meta.unit && (
                            <span className="rounded-md border border-orange-400/20 bg-orange-400/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-orange-400">
                                {meta.unit}
                            </span>
                        )}
                        <AssetBuyButton
                            onClick={() => setBuyTarget({ assetCode: commodity.code, assetName: displayName, price: commodity.price })}
                        />
                    </div>
                </div>

                <div className="mt-3">
                    <p className="font-mono text-xl font-bold text-fg">{money(commodity.price)}</p>
                    <ChangePercentBadge
                        value={commodity.changePercent}
                        positiveIcon={<TrendingUp className="h-3.5 w-3.5" />}
                        negativeIcon={<TrendingDown className="h-3.5 w-3.5" />}
                        size="sm"
                        className="mt-1"
                    >
                        <span className="ml-1 opacity-75">({commodity.changeAmount > 0 ? '+' : ''}{money(commodity.changeAmount)})</span>
                    </ChangePercentBadge>
                </div>

                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                    {usd != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-fg-muted">{t('market.commodity.usdPriceLabel')}</span>
                            <span className="font-mono text-fg">{money(usd, 'USD')}</span>
                        </div>
                    )}
                    {meta.openPrice != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-fg-muted">{t('market.stock.openLabel')}</span>
                            <span className="font-mono text-fg">{money(meta.openPrice)}</span>
                        </div>
                    )}
                    {meta.dayHigh != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><ChevronUp className="h-3 w-3 text-success" />{t('market.stock.highLabel')}</span>
                            <span className="font-mono text-fg">{money(meta.dayHigh)}</span>
                        </div>
                    )}
                    {meta.dayLow != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><ChevronDown className="h-3 w-3 text-danger" />{t('market.stock.lowLabel')}</span>
                            <span className="font-mono text-fg">{money(meta.dayLow)}</span>
                        </div>
                    )}
                    {meta.volume != null && meta.volume > 0 && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-fg-muted">{t('market.stock.volumeLabel')}</span>
                            <span className="font-mono text-fg">{meta.volume.toLocaleString(localeTag)} {t('market.commodity.contractsSuffix')}</span>
                        </div>
                    )}
                </div>

                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {commodity.lastUpdated ? new Date(commodity.lastUpdated).toLocaleString(localeTag, { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                </div>
            </AssetCard>
        );
    };

    const queryParams = {
        ...listParams.params,
        ...(segment && segment !== 'ALL' && { segment }),
    };

    return (
        <MarketListPage
            title={t('market.commodity.title')}
            icon={<Gem className="h-5 w-5" />}
            emptyIcon={<Gem className="h-7 w-7 text-fg-subtle" />}
            marketType="COMMODITY"
            service={commodityService}
            queryKey="commodities"
            listParams={listParams}
            queryParams={queryParams}
            searchPlaceholder={t('market.commodity.searchPlaceholder')}
            countLabel={t('market.commodity.countLabel')}
            sortOptions={sortOptions}
            filterConfig={{
                tabItems,
                activeId: segment,
                onSelect: (id) => listParams.setFilter(id === 'ALL' ? '' : id),
                layoutId: 'commodity-segment',
            }}
            adminTriggers={[
                { key: 'snapshot', label: t('market.admin.snapshot'), title: t('market.admin.snapshotTitle'), fn: adminService.triggerCommoditySnapshot, successMsg: t('market.admin.snapshotStarted'), refetchDelay: 5000 },
                { key: 'candles', label: t('market.admin.candles'), title: t('market.admin.candlesTitle'), fn: adminService.triggerCommodityCandles, successMsg: t('market.admin.candlesStarted') },
                { key: 'full', label: t('market.admin.full'), title: t('market.admin.fullTitle'), fn: adminService.triggerCommodityFull, successMsg: t('market.admin.fullStarted') },
            ]}
            renderCard={renderCard}
            loadingMessage={t('market.commodity.loading')}
            errorMessage={t('market.commodity.error')}
            emptyMessage={t('market.commodity.empty')}
            emptyHint={t('market.empty.adminHint')}
        />
    );
}

export default CommoditiesPage;
