import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Gem, ChevronUp, ChevronDown, Clock } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../shared/components/AnimatedIcons';
import { commodityService } from './commodityService';
import { adminService } from '../admin/services/adminService';
import { formatPrice } from '../../shared/utils/formatters';
import MarketListPage from '../../shared/components/MarketListPage';
import AssetCard from '../../shared/components/AssetCard';
import AssetBuyButton from '../../shared/components/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/ChangePercentBadge';
import useListParams from '../../shared/hooks/useListParams';

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

const SEGMENT_LABELS = {
    PRECIOUS_METAL: 'Kıymetli Metaller',
    OTHER: 'Diğerleri',
};

const SEGMENT_ORDER = ['PRECIOUS_METAL', 'OTHER'];

const formatCommodityPrice = (price) => formatPrice(price, { locale: 'tr-TR' });

function CommoditiesPage() {
    const navigate = useNavigate();
    const listParams = useListParams();
    const segment = listParams.filter || 'ALL';

    const { data: segmentCounts = [] } = useQuery({
        queryKey: ['commoditySegments'],
        queryFn: commodityService.getGroupCounts,
        staleTime: 60_000,
    });

    const tabItems = [...segmentCounts]
        .sort((a, b) => SEGMENT_ORDER.indexOf(a.type) - SEGMENT_ORDER.indexOf(b.type))
        .map(s => ({ type: s.type, count: s.count, label: SEGMENT_LABELS[s.type] || s.type }));

    const renderCard = (commodity, { setBuyTarget }) => {
        const meta = commodity.metadata || {};
        const usd = meta.currentPriceUsd;
        return (
            <AssetCard
                key={commodity.code}
                onClick={() => navigate(`/commodities/${encodeURIComponent(commodity.code)}`)}
                size="sm"
            >
                <div className="flex items-start justify-between">
                    <div className="min-w-0 flex-1">
                        <h3 className="truncate text-sm font-semibold text-fg">{commodity.name || commodity.code}</h3>
                        <span className="block truncate text-xs text-fg-muted">{commodity.code}</span>
                    </div>
                    <div className="flex items-center gap-2">
                        {meta.unit && (
                            <span className="rounded-md border border-orange-400/20 bg-orange-400/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-orange-400">
                                {meta.unit}
                            </span>
                        )}
                        <AssetBuyButton
                            onClick={() => setBuyTarget({ assetCode: commodity.code, assetName: commodity.name || commodity.code, price: commodity.price })}
                        />
                    </div>
                </div>

                <div className="mt-3">
                    <p className="font-mono text-xl font-bold text-fg">₺{formatCommodityPrice(commodity.price)}</p>
                    <ChangePercentBadge
                        value={commodity.changePercent}
                        positiveIcon={<TrendingUp className="h-3.5 w-3.5" />}
                        negativeIcon={<TrendingDown className="h-3.5 w-3.5" />}
                        size="sm"
                        className="mt-1"
                    >
                        <span className="ml-1 opacity-75">({commodity.changeAmount > 0 ? '+' : ''}₺{formatCommodityPrice(commodity.changeAmount)})</span>
                    </ChangePercentBadge>
                </div>

                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                    {usd != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-fg-muted">USD Fiyat</span>
                            <span className="font-mono text-fg">${formatCommodityPrice(usd)}</span>
                        </div>
                    )}
                    {meta.openPrice != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-fg-muted">Açılış</span>
                            <span className="font-mono text-fg">₺{formatCommodityPrice(meta.openPrice)}</span>
                        </div>
                    )}
                    {meta.dayHigh != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><ChevronUp className="h-3 w-3 text-success" />En Yüksek</span>
                            <span className="font-mono text-fg">₺{formatCommodityPrice(meta.dayHigh)}</span>
                        </div>
                    )}
                    {meta.dayLow != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><ChevronDown className="h-3 w-3 text-danger" />En Düşük</span>
                            <span className="font-mono text-fg">₺{formatCommodityPrice(meta.dayLow)}</span>
                        </div>
                    )}
                    {meta.volume != null && meta.volume > 0 && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-fg-muted">Hacim</span>
                            <span className="font-mono text-fg">{meta.volume.toLocaleString('tr-TR')} kontrat</span>
                        </div>
                    )}
                </div>

                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {commodity.lastUpdated ? new Date(commodity.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
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
            title="Emtia"
            icon={<Gem className="h-5 w-5" />}
            emptyIcon={<Gem className="h-7 w-7 text-fg-subtle" />}
            marketType="COMMODITY"
            service={commodityService}
            queryKey="commodities"
            listParams={listParams}
            queryParams={queryParams}
            searchPlaceholder="Emtia ara..."
            countLabel="emtia"
            sortOptions={SORT_OPTIONS}
            filterConfig={{
                tabItems,
                activeId: segment,
                onSelect: (id) => listParams.setFilter(id === 'ALL' ? '' : id),
                layoutId: 'commodity-segment',
            }}
            adminTriggers={[
                { key: 'snapshot', label: 'Snapshot', title: 'Emtia snapshot verilerini güncelle', fn: adminService.triggerCommoditySnapshot, successMsg: 'Emtia snapshot güncelleme başlatıldı', refetchDelay: 5000 },
                { key: 'candles', label: 'Candles (5y)', title: '5 yıllık OHLC verilerini güncelle', fn: adminService.triggerCommodityCandles, successMsg: 'Emtia candle güncelleme başlatıldı' },
                { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + 5y candles)', fn: adminService.triggerCommodityFull, successMsg: 'Emtia tam güncelleme başlatıldı' },
            ]}
            renderCard={renderCard}
            loadingMessage="Emtia verileri yükleniyor…"
            errorMessage="Emtia verileri yüklenirken hata oluştu"
            emptyMessage="Henüz emtia verisi yok."
            emptyHint="Admin butonlarını kullanarak veri çekebilirsiniz."
        />
    );
}

export default CommoditiesPage;
