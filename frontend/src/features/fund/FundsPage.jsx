import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { LineChart, Activity, Clock, Users as UsersIcon, Wallet } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../shared/components/feedback/AnimatedIcons';
import { fundService } from './services/fundService';
import { adminService } from '../admin/services/adminService';
import { formatPriceTRY, formatCompactTRY, formatVolume } from '../../shared/utils/formatters';
import MarketListPage from '../../shared/components/market/MarketListPage';
import AssetCard from '../../shared/components/asset/AssetCard';
import AssetBuyButton from '../../shared/components/asset/AssetBuyButton';
import ChangePercentBadge from '../../shared/components/asset/ChangePercentBadge';
import useListParams from '../../shared/hooks/useListParams';

const FUND_TYPE_LABELS = {
    BYF: 'Borsa Yatırım Fonları',
    YAT: 'Yatırım Fonları',
};

const SORT_OPTIONS = [
    { id: 'changePercent', label: 'Değişim %' },
    { id: 'price', label: 'Fiyat' },
    { id: 'name', label: 'İsim' },
];

function FundsPage() {
    const navigate = useNavigate();
    const listParams = useListParams();
    const typeFilter = listParams.filter || 'ALL';

    const { data: fundTypes = [] } = useQuery({
        queryKey: ['fundTypes'],
        queryFn: fundService.getGroupCounts,
        staleTime: 60_000,
    });

    const queryParams = {
        ...listParams.params,
        ...(typeFilter !== 'ALL' && { subType: typeFilter }),
    };

    const renderCard = (fund, { setBuyTarget }) => {
        const meta = fund.metadata || {};
        return (
            <AssetCard
                key={fund.code}
                onClick={() => navigate(`/funds/${fund.code}`)}
                className="overflow-hidden min-w-0 relative"
            >
                <div className="flex items-start justify-between gap-2 min-w-0">
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                        <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent shrink-0">
                            <LineChart className="w-4.5 h-4.5" />
                        </span>
                        <div className="min-w-0">
                            <h3 className="truncate text-sm font-semibold text-fg">{fund.code}</h3>
                            <span className="block truncate text-xs text-fg-muted">{fund.name || fund.code}</span>
                        </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                        <span className="rounded-md border border-accent/20 bg-accent/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-accent-bright">
                            {meta.fundType || 'FON'}
                        </span>
                        <AssetBuyButton
                            onClick={() => setBuyTarget({ assetCode: fund.code, assetName: fund.name || fund.code, price: fund.price })}
                        />
                    </div>
                </div>

                <div className="mt-3 space-y-1">
                    <span className="block truncate font-mono text-xl font-bold text-fg">
                        {formatPriceTRY(fund.price)}
                    </span>
                    {meta.fundType === 'BYF' && meta.bulletinPrice != null && (
                        <div className="flex items-center gap-2 text-xs text-fg-muted">
                            <span className="font-medium">Borsa Fiyatı</span>
                            <span className="font-mono">{formatPriceTRY(meta.bulletinPrice)}</span>
                        </div>
                    )}
                </div>

                <ChangePercentBadge
                    value={fund.changePercent}
                    positiveIcon={<TrendingUp className="h-3.5 w-3.5" />}
                    negativeIcon={<TrendingDown className="h-3.5 w-3.5" />}
                    size="sm"
                    className="mt-2"
                >
                    <span className="ml-1 opacity-75">24h</span>
                </ChangePercentBadge>

                <div className="mt-3 space-y-1 border-t border-border-default pt-3">
                    {meta.fundType === 'YAT' && meta.investorCount != null && (
                        <div className="flex items-center justify-between text-xs">
                            <span className="flex items-center gap-1 text-fg-muted"><UsersIcon className="h-3 w-3" />Yatırımcı</span>
                            <span className="font-mono text-fg">{formatVolume(meta.investorCount)}</span>
                        </div>
                    )}
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><Wallet className="h-3 w-3" />Portföy</span>
                        <span className="font-mono text-fg">{formatCompactTRY(meta.portfolioSize)}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1 text-fg-muted"><Activity className="h-3 w-3" />Pay Sayısı</span>
                        <span className="font-mono text-fg">{formatVolume(meta.shareCount)}</span>
                    </div>
                </div>

                <div className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {fund.lastUpdated ? new Date(fund.lastUpdated).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }) : 'N/A'}
                </div>
            </AssetCard>
        );
    };

    return (
        <MarketListPage
            title="Yatırım Fonları"
            icon={<LineChart className="h-5 w-5" />}
            emptyIcon={<LineChart className="h-7 w-7 text-fg-subtle" />}
            marketType="FUND"
            service={fundService}
            queryKey="funds"
            listParams={listParams}
            queryParams={queryParams}
            searchPlaceholder="Fon ara..."
            countLabel="fon"
            sortOptions={SORT_OPTIONS}
            filterConfig={{
                tabItems: fundTypes.map(f => ({ type: f.type, count: f.count, label: FUND_TYPE_LABELS[f.type] || f.type })),
                activeId: typeFilter,
                onSelect: (id) => listParams.setFilter(id),
                layoutId: 'fund-type',
            }}
            adminTriggers={[
                { key: 'snapshot', label: 'Snapshot', title: 'Fon snapshot verilerini güncelle', fn: adminService.triggerFundSnapshot, successMsg: 'Fon snapshot güncelleme başlatıldı', refetchDelay: 5000 },
                { key: 'candles', label: 'Candles', title: 'Fon mum verilerini güncelle', fn: adminService.triggerFundCandles, successMsg: 'Fon candle güncelleme başlatıldı' },
                { key: 'full', label: 'Full Update', title: 'Tam güncelleme (snapshot + candles)', fn: adminService.triggerFundFull, successMsg: 'Fon tam güncelleme başlatıldı', refetchDelay: 5000 },
            ]}
            renderCard={renderCard}
            loadingMessage="Fon verileri yükleniyor…"
            errorMessage="Fon verileri yüklenirken hata oluştu"
            emptyMessage="Henüz fon verisi bulunmuyor"
            emptyHint="Admin butonlarını kullanarak veri çekebilirsiniz."
            gridClass="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-h-[600px] content-start"
            animatePresence
        />
    );
}

export default FundsPage;
