import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { BarChart2, ChevronUp, ChevronDown, Activity, Clock } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../shared/components/feedback/AnimatedIcons';
import { stockService } from './services/stockService';
import { adminService } from '../admin/services/adminService';
import { getChangeClass, changeColors, changeBg, formatPrice, formatVolume, formatPercentAbs } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/asset/ChangePercentBadge';
import useListParams from '../../shared/hooks/useListParams';
import { assetCodeLabel } from '../../shared/utils/assetCode';

const SORT_OPTION_IDS = ['changePercent', 'price', 'name'];
const SEGMENT_IDS = ['MAIN_INDEX', 'SECONDARY_INDEX', 'EQUITY'];

const formatStockPrice = (price) => formatPrice(price);

function StocksPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const listParams = useListParams();
    const segment = listParams.filter || 'EQUITY';

    const sortOptions = SORT_OPTION_IDS.map(id => ({ id, label: t(`market.sort.${id}`) }));
    const segmentLabel = (id) => t(`market.stockSegments.${id}`);

    const { data: segmentCounts = [] } = useQuery({
        queryKey: ['stockSegments'],
        queryFn: stockService.getGroupCounts,
        staleTime: 60_000,
    });

    const { data: indicesData } = useQuery({
        queryKey: ['stocks', 'indices'],
        queryFn: () => stockService.getAll({ segment: 'MAIN_INDEX', size: 10 }),
    });
    const indices = indicesData?.content || [];

    const tabItems = segmentCounts
        .filter(s => s.type !== 'MAIN_INDEX')
        .map(s => ({ type: s.type, count: s.count, label: segmentLabel(s.type) }));

    const queryParams = { ...listParams.params, segment };

    const indicesSection = indices.length > 0 ? (
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
                            <span className="text-xs text-fg-muted">{t('market.stockSegments.indexLabel')}</span>
                        </div>
                        <p className="mt-3 font-mono text-2xl font-bold text-fg">{formatStockPrice(index.price)}</p>
                        <div className={`mt-2 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${changeBg[cls]} ${changeColors[cls]}`}>
                            {index.changePercent > 0 ? <ChevronUp className="h-3.5 w-3.5" /> : index.changePercent < 0 ? <ChevronDown className="h-3.5 w-3.5" /> : null}
                            {formatPercentAbs(index.changePercent)}
                        </div>
                    </motion.div>
                );
            })}
        </motion.div>
    ) : null;

    const renderCard = (stock, { setBuyTarget }) => (
        <AssetCard
            key={stock.code}
            onClick={() => navigate(`/stocks/${stock.code}`)}
            size="sm"
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
                    <AssetBuyButton
                        onClick={() => setBuyTarget({ assetCode: stock.code, assetName: stock.name || stock.code, price: stock.price })}
                    />
                </div>
            </div>

            <div className="mt-3">
                <p className="font-mono text-xl font-bold text-fg">₺{formatStockPrice(stock.price)}</p>
                <ChangePercentBadge
                    value={stock.changePercent}
                    positiveIcon={<TrendingUp className="h-3.5 w-3.5" />}
                    negativeIcon={<TrendingDown className="h-3.5 w-3.5" />}
                    size="sm"
                    className="mt-1"
                >
                    <span className="ml-1 opacity-75">({stock.changeAmount > 0 ? '+' : ''}₺{formatStockPrice(stock.changeAmount)})</span>
                </ChangePercentBadge>
            </div>

            <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                {stock.metadata?.openPrice != null && (
                    <div className="flex items-center justify-between text-xs">
                        <span className="text-fg-muted">{t('market.stock.openLabel')}</span>
                        <span className="font-mono text-fg">₺{formatStockPrice(stock.metadata.openPrice)}</span>
                    </div>
                )}
                {stock.metadata?.dayHigh != null && (
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><ChevronUp className="h-3 w-3 text-success" />{t('market.stock.highLabel')}</span>
                        <span className="font-mono text-fg">₺{formatStockPrice(stock.metadata.dayHigh)}</span>
                    </div>
                )}
                {stock.metadata?.dayLow != null && (
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><ChevronDown className="h-3 w-3 text-danger" />{t('market.stock.lowLabel')}</span>
                        <span className="font-mono text-fg">₺{formatStockPrice(stock.metadata.dayLow)}</span>
                    </div>
                )}
                <div className="flex items-center justify-between text-xs">
                    <span className="text-fg-muted">{t('market.stock.volumeLabel')}</span>
                    <span className="font-mono text-fg">{formatVolume(stock.metadata?.volume)}</span>
                </div>
            </div>

            <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                <Clock className="h-3 w-3" />
                {stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleString(t('common.localeTag'), { timeZone: 'Europe/Istanbul' }) : 'N/A'}
            </div>
        </AssetCard>
    );

    return (
        <MarketListPage
            title={t('market.stock.title')}
            icon={<Activity className="h-5 w-5" />}
            emptyIcon={<BarChart2 className="h-7 w-7 text-fg-subtle" />}
            marketType="STOCK"
            service={stockService}
            queryKey="stocks"
            listParams={listParams}
            queryParams={queryParams}
            searchPlaceholder={t('market.stock.searchPlaceholder')}
            countLabel={t('market.stock.countLabel')}
            sortOptions={sortOptions}
            filterConfig={{
                tabItems,
                activeId: segment,
                onSelect: (id) => listParams.setFilter(id),
                layoutId: 'stock-segment',
            }}
            filterShowAll={false}
            adminTriggers={[
                { key: 'snapshot', label: t('market.admin.snapshot'), title: t('market.admin.snapshotTitle'), fn: adminService.triggerStockSnapshot, successMsg: t('market.admin.snapshotStarted'), refetchDelay: 5000 },
                { key: 'candles', label: t('market.admin.candles'), title: t('market.admin.candlesTitle'), fn: adminService.triggerStockCandles, successMsg: t('market.admin.candlesStarted') },
                { key: 'full', label: t('market.admin.full'), title: t('market.admin.fullTitle'), fn: adminService.triggerStockFull, successMsg: t('market.admin.fullStarted') },
            ]}
            preGridChildren={indicesSection}
            renderCard={renderCard}
            loadingMessage={t('market.stock.loading')}
            errorMessage={t('market.stock.error')}
            emptyMessage={t('market.stock.empty')}
            emptyHint={t('market.empty.adminHint')}
            gridClass="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
        />
    );
}
export default StocksPage;
