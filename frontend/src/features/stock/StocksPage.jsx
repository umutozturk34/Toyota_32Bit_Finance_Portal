import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { BarChart2, ChevronUp, ChevronDown, Activity, Clock } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../shared/components/AnimatedIcons';
import { stockService } from './stockService';
import { adminService } from '../admin/adminService';
import { getChangeClass, changeColors, changeBg, formatPrice, formatVolume, formatPercentAbs } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import MarketListPage from '../../shared/components/MarketListPage';
import AssetCard from '../../shared/components/AssetCard';
import AssetBuyButton from '../../shared/components/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/ChangePercentBadge';
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

const formatStockPrice = (price) => formatPrice(price, { locale: 'tr-TR' });

function StocksPage() {
    const navigate = useNavigate();
    const listParams = useListParams();
    const segment = listParams.filter || 'EQUITY';

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
        .map(s => ({ type: s.type, count: s.count, label: SEGMENT_LABELS[s.type] || s.type }));

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
                            <span className="text-xs text-fg-muted">Endeks</span>
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
        </AssetCard>
    );

    return (
        <MarketListPage
            title="Borsa İstanbul (BIST)"
            icon={<Activity className="h-5 w-5" />}
            emptyIcon={<BarChart2 className="h-7 w-7 text-fg-subtle" />}
            marketType="STOCK"
            service={stockService}
            queryKey="stocks"
            listParams={listParams}
            queryParams={queryParams}
            searchPlaceholder="Hisse ara..."
            countLabel="hisse"
            sortOptions={SORT_OPTIONS}
            filterConfig={{
                tabItems,
                activeId: segment,
                onSelect: (id) => listParams.setFilter(id),
                layoutId: 'stock-segment',
            }}
            filterShowAll={false}
            adminTriggers={[
                { key: 'snapshot', label: 'Snapshot', title: 'Hisse snapshot verilerini güncelle (fiyat, hacim vb.)', fn: adminService.triggerStockSnapshot, successMsg: 'Hisse snapshot güncelleme başlatıldı', refetchDelay: 5000 },
                { key: 'candles', label: 'Candles (5y)', title: '5 yıllık OHLC verilerini güncelle (10-15 dakika)', fn: adminService.triggerStockCandles, successMsg: 'Hisse candle güncelleme başlatıldı (Bu işlem 10-15 dakika sürebilir)' },
                { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + 5y candles, 15-20 dakika)', fn: adminService.triggerStockFull, successMsg: 'Hisse tam güncelleme başlatıldı (Bu işlem 15-20 dakika sürebilir)' },
            ]}
            preGridChildren={indicesSection}
            renderCard={renderCard}
            loadingMessage="Hisse verileri yükleniyor…"
            errorMessage="Hisse senedi verileri yüklenirken hata oluştu"
            emptyMessage="Henüz hisse senedi verisi yok."
            emptyHint="Admin butonlarını kullanarak veri çekebilirsiniz."
            gridClass="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
        />
    );
}
export default StocksPage;
