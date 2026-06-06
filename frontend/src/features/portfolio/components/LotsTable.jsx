import { motion } from 'framer-motion';
import { Layers, Pencil, Trash2, ShoppingBag, XCircle, RotateCcw } from 'lucide-react';
import { formatPercent, changeColors, getChangeClass, currentLocaleTag } from '../../../shared/utils/formatters';
import PositionStatusBadge from './PositionStatusBadge';

const formatEntryDate = (v) => v ? new Date(v).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

// directionSign for a lot: −1 for a VIOP SHORT, +1 otherwise. The frame's notional change (value − cost) is
// backwards for a SHORT (its converted notional falls as it profits), so the sign flips its USD/EUR K/Z.
// Prefer the nested derivative.direction; fall back to the "SHORT · …" assetName prefix used elsewhere.
const lotDirectionSign = (lot) => {
  if (lot.assetType !== 'VIOP') return 1;
  const direction = lot.derivative?.direction || String(lot.assetName || '').split(' · ')[0];
  return direction === 'SHORT' ? -1 : 1;
};

// VIOP value shown in the Lots table must be DIRECTION-AWARE EQUITY (entry notional + signed PnL), matching the
// asset chart (assetChartBuilder equityTry = costTry + pnlTry) and the portfolio summary card. Raw marketValueTry
// is the direction-blind notional, which FALLS as a SHORT profits and reads as "value leaving". Spot/LONG:
// entryValueTry + pnlTry === marketValueTry, so this is a no-op there.
const lotDisplayValueTry = (lot) => {
  const market = Number(lot.marketValueTry);
  if (lot.assetType !== 'VIOP') return market;
  const entry = Number(lot.entryValueTry);
  const pnl = Number(lot.pnlTry);
  return Number.isFinite(entry) && Number.isFinite(pnl) ? entry + pnl : market;
};

// Entry notional in TRY: the backend's entryValueTry (NOT entryPrice × quantity, which is wrong for a VIOP
// whose notional carries contract size/direction). Falls back to price × qty only when the backend omits it.
const lotEntryValueTry = (lot) => (lot.entryValueTry != null
  ? Number(lot.entryValueTry)
  : Number(lot.entryPrice) * Number(lot.quantity));

export function LotsTable({ lots, t, money, bigMoney, nativeCurrency, frame, convertAt, onEditLot, onSellLot, onReopenLot, onDeleteLot }) {
  const today = new Date().toLocaleDateString('sv-SE');
  // Per-lot K/Z in the display-currency frame (entry cost @ entry-date FX, value @ today/exit FX) via the
  // universal frame() — so a EUR lot reads ~0% in EUR, not the lira's +2837%. Closed lots value at exit date.
  // Cost basis uses the backend entryValueTry directly (entryPrice × quantity is WRONG for a VIOP, whose
  // entry notional ≠ price × qty once contractSize/direction apply); directionSign keeps a SHORT's K/Z right.
  const lotFrameFor = (lot) => frame(
    lotEntryValueTry(lot),
    Number(lot.marketValueTry),
    lot.entryDate,
    lot.exitDate || today,
    lot.pnlTry,
    lot.pnlPercent,
    lotDirectionSign(lot),
  );
  // Value-cell text + hover title for a lot, direction-aware AND currency-aware. For a USD/EUR frame, the
  // equity is the per-leg frame value (entry cost @ entry-date FX + the frame's K/Z) — the SAME convention as
  // PositionsTable and the K/Z column — NOT the TRY equity scalar converted at a single rate, which would
  // FX the entry leg at today's rate. TRY/ORIGINAL display keeps the money() scalar path (money converts the
  // TRY equity to the display/native currency itself).
  const lotValueParts = (lot, lotFrame, isLotClosed) => {
    if (lotFrame.base !== 'TRY' && convertAt) {
      const cost = convertAt(lotEntryValueTry(lot), 'TRY', String(lot.entryDate).slice(0, 10), nativeCurrency);
      if (cost != null) {
        const value = money(cost + lotFrame.pnl, lotFrame.base);
        return { text: value, title: value };
      }
    }
    const title = money(lotDisplayValueTry(lot), 'TRY',
      isLotClosed ? { dateAt: lot.exitDate, natural: nativeCurrency } : { natural: nativeCurrency });
    const text = isLotClosed ? title : bigMoney(lotDisplayValueTry(lot));
    return { text, title };
  };
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
          const lotFrame = lotFrameFor(lot);
          const isLotClosed = !!lot.exitDate;
          const lotPnlClass = getChangeClass(lotFrame.pnl);
          const lotPnlText = lotFrame.base !== 'TRY'
            ? money(lotFrame.pnl, lotFrame.base)
            : (isLotClosed ? money(lotFrame.pnl, 'TRY', { dateAt: lot.exitDate, natural: nativeCurrency }) : bigMoney(lotFrame.pnl));
          const lotValue = lotValueParts(lot, lotFrame, isLotClosed);
          const actions = (
            <div className="flex items-center justify-start gap-1">
              {!isLotClosed && onEditLot && (
                <button
                  onClick={() => onEditLot(lot)}
                  className="flex items-center justify-center w-7 h-7 md:w-6 md:h-6 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer"
                  aria-label={t('common.edit')}
                >
                  <Pencil className="h-3 w-3" />
                </button>
              )}
              {!isLotClosed && onSellLot && (
                <button
                  onClick={() => onSellLot(lot)}
                  className="flex items-center justify-center w-7 h-7 md:w-6 md:h-6 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer"
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
                  className="flex items-center justify-center w-7 h-7 md:w-6 md:h-6 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer"
                  aria-label={t('portfolio.reopen.title', 'Tekrar aç')}
                >
                  <RotateCcw className="h-3 w-3" />
                </button>
              )}
              {onDeleteLot && (
                <button
                  onClick={() => onDeleteLot(lot)}
                  className="flex items-center justify-center w-7 h-7 md:w-6 md:h-6 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer"
                  aria-label={t('common.delete')}
                >
                  <Trash2 className="h-3 w-3" />
                </button>
              )}
            </div>
          );
          return (
            <motion.div
              key={lot.id}
              layout
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              className={`hidden md:grid md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_72px_minmax(0,0.7fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1fr)] gap-3 items-center px-3 py-2 rounded-lg border border-border-default min-w-0 ${
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
                {money(lot.entryPrice, 'TRY', { dateAt: lot.entryDate, natural: nativeCurrency })}
              </span>
              <span className="text-[11px] font-mono text-fg text-left truncate" title={lotValue.title}>
                {lotValue.text}
              </span>
              <div className="text-left min-w-0">
                <p className={`text-[11px] font-mono font-semibold ${changeColors[lotPnlClass]} truncate`} title={lotPnlText}>
                  {lotPnlText}
                </p>
                <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono ${changeColors[lotPnlClass]}`}>
                  {formatPercent(lotFrame.pnlPercent)}
                </span>
              </div>
              {actions}
            </motion.div>
          );
        })}
        {lots.map((lot) => {
          const lotFrame = lotFrameFor(lot);
          const isLotClosed = !!lot.exitDate;
          const lotPnlClass = getChangeClass(lotFrame.pnl);
          const lotPnlText = lotFrame.base !== 'TRY'
            ? money(lotFrame.pnl, lotFrame.base)
            : (isLotClosed ? money(lotFrame.pnl, 'TRY', { dateAt: lot.exitDate, natural: nativeCurrency }) : bigMoney(lotFrame.pnl));
          const lotValue = lotValueParts(lot, lotFrame, isLotClosed);
          return (
            <motion.div
              key={`m-${lot.id}`}
              layout
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              className={`md:hidden p-3 rounded-lg border border-border-default min-w-0 space-y-2 ${
                isLotClosed ? 'bg-bg-elevated/50 opacity-70' : 'bg-bg-elevated'
              } transition-colors`}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <PositionStatusBadge closed={isLotClosed} isDerivative={lot.assetType === 'VIOP'} />
                  <span className="text-[11px] font-mono text-fg-muted truncate">
                    {formatEntryDate(lot.entryDate)}
                    {lot.exitDate ? ` → ${formatEntryDate(lot.exitDate)}` : ''}
                  </span>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  {!isLotClosed && onEditLot && (
                    <button
                      onClick={() => onEditLot(lot)}
                      className="flex items-center justify-center w-7 h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer"
                      aria-label={t('common.edit')}
                    >
                      <Pencil className="h-3 w-3" />
                    </button>
                  )}
                  {!isLotClosed && onSellLot && (
                    <button
                      onClick={() => onSellLot(lot)}
                      className="flex items-center justify-center w-7 h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer"
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
                      className="flex items-center justify-center w-7 h-7 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer"
                      aria-label={t('portfolio.reopen.title', 'Tekrar aç')}
                    >
                      <RotateCcw className="h-3 w-3" />
                    </button>
                  )}
                  {onDeleteLot && (
                    <button
                      onClick={() => onDeleteLot(lot)}
                      className="flex items-center justify-center w-7 h-7 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer"
                      aria-label={t('common.delete')}
                    >
                      <Trash2 className="h-3 w-3" />
                    </button>
                  )}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2 text-[11px]">
                <div className="rounded-md bg-bg-base/60 px-2 py-1.5">
                  <p className="text-fg-muted text-[10px]">{t('portfolio.positions.quantityCol')}</p>
                  <p className="font-mono text-fg truncate">{Number(lot.quantity).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 6 })}</p>
                </div>
                <div className="rounded-md bg-bg-base/60 px-2 py-1.5">
                  <p className="text-fg-muted text-[10px]">{t('portfolio.positions.entryPriceCol')}</p>
                  <p className="font-mono text-fg truncate">{money(lot.entryPrice, 'TRY', { dateAt: lot.entryDate, natural: nativeCurrency })}</p>
                </div>
                <div className="rounded-md bg-bg-base/60 px-2 py-1.5">
                  <p className="text-fg-muted text-[10px]">{t('portfolio.positions.marketValueCol')}</p>
                  <p className="font-mono text-fg truncate" title={lotValue.title}>{lotValue.text}</p>
                </div>
                <div className="rounded-md bg-bg-base/60 px-2 py-1.5">
                  <p className="text-fg-muted text-[10px]">{t('portfolio.positions.pnlCol')}</p>
                  <p className={`font-mono font-semibold ${changeColors[lotPnlClass]} truncate`} title={lotPnlText}>
                    {lotPnlText} <span className="text-[10px]">({formatPercent(lotFrame.pnlPercent)})</span>
                  </p>
                </div>
              </div>
            </motion.div>
          );
        })}
      </div>
    </>
  );
}
