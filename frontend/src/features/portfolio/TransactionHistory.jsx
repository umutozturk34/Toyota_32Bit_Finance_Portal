import { useMemo } from 'react';
import { motion } from 'framer-motion';
import { History } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight } from '../../shared/components/AnimatedIcons';
import { formatPriceTRY } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import EmptyState from '../../shared/components/EmptyState';
import SearchInput from '../../shared/components/SearchInput';
import SortSelect from '../../shared/components/SortSelect';
import FilterTabs from '../../shared/components/FilterTabs';
import Pagination from '../../shared/components/Pagination';
import { ASSET_TYPE_LABELS, ASSET_TYPE_FILTERS } from '../../shared/constants/assetTypes';
import { assetCodeLabel } from '../../shared/constants/commodities';
import { usePortfolioTransactions } from './usePortfolioData';
import useListParams from '../../shared/hooks/useListParams';

const SORT_OPTIONS = [
  { id: 'createdAt', label: 'Tarih' },
  { id: 'totalCostTry', label: 'Toplam Tutar' },
  { id: 'assetCode', label: 'Varlık Kodu' },
];

function formatTxnDate(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Europe/Istanbul' });
}

function formatTxnTime(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Istanbul' });
}

export default function TransactionHistory({ portfolioId }) {
  const listParams = useListParams({ defaultSize: 8, prefix: 'txn' });
  const assetTypeFilter = listParams.filter || '';

  const queryParams = {
    ...listParams.params,
    ...(assetTypeFilter && { assetType: assetTypeFilter }),
  };

  const { data } = usePortfolioTransactions(portfolioId, queryParams);
  const transactions = data?.content || [];
  const totalPages = data?.totalPages || 0;

  const grouped = useMemo(() => {
    if (!transactions.length) return [];
    const groups = {};
    for (const txn of transactions) {
      const dateKey = txn.createdAt ? txn.createdAt.split('T')[0] : 'unknown';
      if (!groups[dateKey]) groups[dateKey] = [];
      groups[dateKey].push(txn);
    }
    const dir = listParams.direction === 'asc' ? 1 : -1;
    return Object.entries(groups).sort(([a], [b]) => dir * a.localeCompare(b));
  }, [transactions]);

  if (!portfolioId) return null;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="w-48">
          <SearchInput value={listParams.search} onChange={listParams.setSearch} placeholder="İşlem ara..." />
        </div>
        <SortSelect
          value={listParams.sort}
          direction={listParams.direction}
          options={SORT_OPTIONS}
          onSortChange={listParams.setSort}
          onDirectionChange={listParams.setDirection}
        />
        <FilterTabs
          items={ASSET_TYPE_FILTERS.filter(f => f.id).map(f => ({ type: f.id, label: f.label }))}
          activeId={assetTypeFilter || 'ALL'}
          onSelect={(id) => listParams.setFilter(id === 'ALL' ? '' : id)}
          showAll={true}
          layoutId="txn-type"
        />
      </div>

      {transactions.length === 0 ? (
        <EmptyState
          icon={<History className="h-8 w-8 text-fg-muted" />}
          message={listParams.search ? 'Aramayla eşleşen işlem bulunamadı.' : 'Henüz işlem bulunmuyor'}
        />
      ) : (
        <motion.div variants={containerVariants(0.04)} initial="hidden" animate="show" className="space-y-4 min-h-[500px]">
          {grouped.map(([dateKey, txns]) => (
            <div key={dateKey} className="space-y-2">
              <div className="flex items-center gap-3 py-1">
                <span className="h-px flex-1 bg-border-default" />
                <span className="text-[11px] font-medium text-fg-muted px-1">{formatTxnDate(txns[0].createdAt)}</span>
                <span className="h-px flex-1 bg-border-default" />
              </div>

              {txns.map((txn) => {
                const isBuy = txn.side === 'BUY';
                return (
                  <motion.div key={txn.id} variants={cardVariants} className={`rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md card-hover transition-all duration-200 hover:border-border-hover border-l-2 ${isBuy ? 'border-l-success' : 'border-l-danger'}`}>
                    <div className="hidden md:flex items-center gap-4 p-4">
                      <div className="flex items-center gap-3 w-24 shrink-0">
                        <span className={`flex items-center justify-center w-8 h-8 rounded-lg ${isBuy ? 'bg-success/10' : 'bg-danger/10'}`}>
                          {isBuy ? <ArrowDownRight className="h-4 w-4 text-success" /> : <ArrowUpRight className="h-4 w-4 text-danger" />}
                        </span>
                        <span className={`text-xs font-semibold ${isBuy ? 'text-success' : 'text-danger'}`}>{isBuy ? 'ALIŞ' : 'SATIŞ'}</span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-fg">{assetCodeLabel(txn.assetType, txn.assetCode)}</p>
                        <p className="text-[11px] text-fg-muted">{ASSET_TYPE_LABELS[txn.assetType] || txn.assetType}</p>
                      </div>
                      <div className="text-right w-28">
                        <p className="text-xs text-fg-muted">Miktar</p>
                        <p className="text-sm font-mono text-fg">{Number(txn.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</p>
                      </div>
                      <div className="text-right w-32">
                        <p className="text-xs text-fg-muted">Birim Fiyat</p>
                        <p className="text-sm font-mono text-fg">{formatPriceTRY(txn.unitPriceTry)}</p>
                      </div>
                      <div className="text-right w-32">
                        <p className="text-xs text-fg-muted">Toplam</p>
                        <p className="text-sm font-mono font-semibold text-fg">{formatPriceTRY(txn.totalCostTry)}</p>
                      </div>
                      <div className="text-right w-20 shrink-0">
                        <p className="text-[11px] text-fg-muted">{formatTxnTime(txn.createdAt)}</p>
                      </div>
                    </div>

                    <div className="md:hidden p-4 space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                          <span className={`flex items-center justify-center w-8 h-8 rounded-lg ${isBuy ? 'bg-success/10' : 'bg-danger/10'}`}>
                            {isBuy ? <ArrowDownRight className="h-4 w-4 text-success" /> : <ArrowUpRight className="h-4 w-4 text-danger" />}
                          </span>
                          <div>
                            <p className="text-sm font-semibold text-fg">{assetCodeLabel(txn.assetType, txn.assetCode)}</p>
                            <p className="text-[11px] text-fg-muted">{ASSET_TYPE_LABELS[txn.assetType] || txn.assetType}</p>
                          </div>
                        </div>
                        <span className={`text-xs font-semibold px-2 py-0.5 rounded-md ${isBuy ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'}`}>
                          {isBuy ? 'ALIŞ' : 'SATIŞ'}
                        </span>
                      </div>
                      <div className="grid grid-cols-3 gap-2 text-xs">
                        <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Miktar</p><p className="font-mono text-fg font-medium">{Number(txn.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</p></div>
                        <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Birim</p><p className="font-mono text-fg font-medium">{formatPriceTRY(txn.unitPriceTry)}</p></div>
                        <div className="rounded-lg bg-bg-base px-2.5 py-2"><p className="text-fg-muted mb-0.5">Toplam</p><p className="font-mono text-fg font-semibold">{formatPriceTRY(txn.totalCostTry)}</p></div>
                      </div>
                    </div>
                  </motion.div>
                );
              })}
            </div>
          ))}
        </motion.div>
      )}

      <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />
    </div>
  );
}
