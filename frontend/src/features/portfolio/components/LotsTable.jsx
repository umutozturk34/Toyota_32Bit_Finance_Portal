import { motion } from 'framer-motion';
import { Layers, Pencil, Trash2, ShoppingBag, XCircle, RotateCcw } from 'lucide-react';
import { formatPercent, changeColors, getChangeClass, currentLocaleTag } from '../../../shared/utils/formatters';
import PositionStatusBadge from './PositionStatusBadge';

const formatEntryDate = (v) => v ? new Date(v).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

export function LotsTable({ lots, t, money, bigMoney, onEditLot, onSellLot, onReopenLot, onDeleteLot }) {
  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Layers className="h-4 w-4 text-accent" />
          <span className="text-sm font-semibold text-fg">
            {t('assetDetail.lots.title', { defaultValue: 'Lotlar' })}
          </span>
          <span className="text-[10px] font-mono text-fg-muted bg-bg-elevated px-1.5 py-0.5 rounded">
            {lots.length}
          </span>
        </div>
      </div>

      <div className="hidden md:grid md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_72px_minmax(0,0.7fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1fr)] gap-3 px-3 py-1 text-[10px] uppercase tracking-wider text-fg-muted font-medium whitespace-nowrap">
        <span>{t('portfolio.positions.entryDateCol')}</span>
        <span>{t('portfolio.positions.exitDateLabel')}</span>
        <span>{t('portfolio.positions.statusCol')}</span>
        <span>{t('portfolio.positions.quantityCol')}</span>
        <span>{t('portfolio.positions.entryPriceCol')}</span>
        <span>{t('portfolio.positions.marketValueCol')}</span>
        <span>{t('portfolio.positions.pnlCol')}</span>
        <span>{t('portfolio.positions.actionsCol')}</span>
      </div>

      <div className="space-y-1.5">
        {lots.map((lot) => {
          const lotPnlClass = getChangeClass(lot.pnlTry);
          const isLotClosed = !!lot.exitDate;
          return (
            <motion.div
              key={lot.id}
              layout
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              className={`grid grid-cols-2 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_72px_minmax(0,0.7fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1fr)] gap-3 items-center px-3 py-2 rounded-lg border border-border-default min-w-0 ${
                isLotClosed ? 'bg-bg-elevated/50 opacity-70' : 'bg-bg-elevated hover:border-accent/40'
              } transition-colors`}
            >
              <span className="text-[11px] font-mono text-fg-muted text-left truncate">
                {formatEntryDate(lot.entryDate)}
              </span>
              <span className="text-[11px] font-mono text-fg-muted text-left truncate">
                {lot.exitDate ? formatEntryDate(lot.exitDate) : '—'}
              </span>
              <div className="flex justify-start">
                <PositionStatusBadge closed={isLotClosed} isDerivative={lot.assetType === 'VIOP'} />
              </div>
              <span className="text-[11px] font-mono text-fg text-left truncate">
                {Number(lot.quantity).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 6 })}
              </span>
              <span className="text-[11px] font-mono text-fg text-left truncate">
                {money(lot.entryPrice)}
              </span>
              <span className="text-[11px] font-mono text-fg text-left truncate" title={money(lot.marketValueTry)}>
                {bigMoney(lot.marketValueTry)}
              </span>
              <div className="text-left min-w-0">
                <p className={`text-[11px] font-mono font-semibold ${changeColors[lotPnlClass]} truncate`} title={money(lot.pnlTry)}>
                  {bigMoney(lot.pnlTry)}
                </p>
                <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono ${changeColors[lotPnlClass]}`}>
                  {formatPercent(lot.pnlPercent)}
                </span>
              </div>
              <div className="flex justify-start gap-1">
                {!isLotClosed && onEditLot && (
                  <button
                    onClick={() => onEditLot(lot)}
                    className="flex items-center justify-center w-6 h-6 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer"
                    aria-label={t('common.edit')}
                  >
                    <Pencil className="h-3 w-3" />
                  </button>
                )}
                {!isLotClosed && onSellLot && (
                  <button
                    onClick={() => onSellLot(lot)}
                    className="flex items-center justify-center w-6 h-6 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer"
                    aria-label={lot.assetType === 'VIOP'
                      ? t('portfolio.derivatives.closeTitle', 'Pozisyon Kapat')
                      : t('portfolio.sell.title', { code: lot.assetCode })}
                  >
                    {lot.assetType === 'VIOP'
                      ? <XCircle className="h-3 w-3" />
                      : <ShoppingBag className="h-3 w-3" />}
                  </button>
                )}
                {isLotClosed && onReopenLot && (
                  <button
                    onClick={() => onReopenLot(lot)}
                    className="flex items-center justify-center w-6 h-6 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer"
                    aria-label={t('portfolio.reopen.title', 'Tekrar aç')}
                  >
                    <RotateCcw className="h-3 w-3" />
                  </button>
                )}
                {onDeleteLot && (
                  <button
                    onClick={() => onDeleteLot(lot)}
                    className="flex items-center justify-center w-6 h-6 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer"
                    aria-label={t('common.delete')}
                  >
                    <Trash2 className="h-3 w-3" />
                  </button>
                )}
              </div>
            </motion.div>
          );
        })}
      </div>
    </>
  );
}
