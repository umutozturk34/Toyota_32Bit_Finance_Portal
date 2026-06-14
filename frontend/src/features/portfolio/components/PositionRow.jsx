import { useNavigate } from 'react-router-dom';
import { ChevronRight, ExternalLink, Pencil, Trash2, XCircle, ShoppingBag, RotateCcw } from 'lucide-react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { cardVariants } from '../../../shared/utils/animations';
import { formatPercent, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { resolveNativeCurrency } from '../lib/positionFormHelpers';
import { assetCodeLabel } from '../../../shared/utils/assetCode';
import { commodityLabel } from '../../../shared/utils/commodityName';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import PositionStatusBadge from './PositionStatusBadge';
import PositionAssetBadge from './PositionAssetBadge';
import PositionDerivativeChips from './PositionDerivativeChips';
import SelectableCheckbox from './SelectableCheckbox';
import { formatEntryDate, marketHref } from '../lib/positionsTableHelpers';

export default function PositionRow({ pos, pending, elapsed, selected, onToggleSelect, onAssetClick, onEditClick, onDeleteClick, onCloseClick, onSellClick, onReopenClick }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { format: money, formatCompact: moneyCompact, resolveTarget, convert } = useMoney();
  const nativeCurrency = resolveNativeCurrency({ assetType: pos.assetType, assetCode: pos.assetCode });
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000, nativeCurrency);
  const localeTag = t('common.localeTag');
  const assetTypeLabel = t(`assets.labels.${pos.assetType}`, { defaultValue: pos.assetType });
  const isDerivative = pos.assetType === 'VIOP';
  const isClosedDerivative = isDerivative && pos.assetName && pos.assetName.includes('KAPALI');
  const isClosedSpot = !isDerivative && !!pos.exitDate;
  const derivativeName = isDerivative ? pos.derivative?.displayName : null;
  const displayName = commodityLabel(t, pos.assetType, pos.assetCode,
    pos.assetName && pos.assetName !== pos.assetCode
      ? pos.assetName.replace(/\s·\sKAPALI$/, '')
      : assetTypeLabel);
  const showEdit = !isClosedSpot;
  const showCloseButton = isDerivative && !isClosedDerivative;
  const showSellButton = !isDerivative && !isClosedSpot;
  const showReopenButton = isClosedSpot || isClosedDerivative;
  const isClosed = isClosedSpot || isClosedDerivative;
  // Closed positions are frozen at exit (currentPriceTry/marketValueTry hold the exit price in TRY at
  // the close date), so convert those at the exit date; open positions use today's spot rate.
  const closedFx = isClosed
    ? { natural: nativeCurrency, dateAt: pos.exitDate }
    : { natural: nativeCurrency };

  // Display-currency PnL must be value@today/exit − cost@entry-date, NOT the TRY PnL (a value−cost
  // difference) converted at one FX — which is catastrophically wrong when the lot's entry-date FX differs
  // from today's (e.g. a USD lot bought in 1995: the TRY PnL is ~+104000% but in USD it is ~0%). So convert
  // the cost at its entry date and the value at today/exit, then difference. In TRY the backend figures are
  // already correct, so only a USD/EUR (or ORIGINAL non-TRY) frame recomputes.
  const frameCcy = resolveTarget('TRY', nativeCurrency);
  const isNonTryFrame = frameCcy !== 'TRY';
  // Use the backend's entry value directly. Deriving it as marketValue − pnlTry is WRONG for a VIOP SHORT,
  // whose direction-aware pnl ≠ value − cost — that corrupted both the entry cost and the USD/EUR K/Z.
  const entryValueTry = pos.entryValueTry != null
    ? Number(pos.entryValueTry)
    : Number(pos.marketValueTry) - Number(pos.pnlTry);
  const costFrame = isNonTryFrame ? convert(entryValueTry, 'TRY', nativeCurrency, pos.entryDate) : null;
  const valueFrame = isNonTryFrame
    ? convert(pos.marketValueTry, 'TRY', nativeCurrency, isClosed ? pos.exitDate : undefined)
    : null;
  // VIOP K/Z is DIRECTION-AWARE: a SHORT profits as its notional (value) falls, so value − cost is backwards.
  // Apply the canonical directionSign × (value − cost) — the SAME rule as useRateHistory.frame() and the
  // backend MultiCurrencyPnlCalculator.pointFrame — instead of grafting the TRY PnL's sign onto the magnitude
  // (which mis-signed a frame whose per-date FX move alone disagreed with the TRY direction). −1 only for a
  // VIOP SHORT (derivative.direction, else the "SHORT · …" assetName prefix); spot/LONG stay +1 → a no-op.
  const isShortDerivative = isDerivative
    && (pos.derivative?.direction || String(pos.assetName || '').split(' · ')[0]) === 'SHORT';
  const directionSign = isShortDerivative ? -1 : 1;
  const framePnl = costFrame != null && valueFrame != null
    ? directionSign * (valueFrame - costFrame)
    : null;
  const framePnlPct = framePnl != null && costFrame ? (framePnl / Math.abs(costFrame)) * 100 : null;
  // TOPLAM column shows EQUITY = cost + K/Z (matches the donut/card), not the raw notional. For spot/LONG
  // equity == notional; for a profiting VIOP SHORT equity rises above cost while notional falls.
  const totalEquityTry = entryValueTry + Number(pos.pnlTry);
  const useFrame = isNonTryFrame && framePnl != null;
  const pnlClass = getChangeClass(useFrame ? framePnl : pos.pnlTry);
  const shownPnlPct = useFrame ? framePnlPct : Number(pos.pnlPercent);
  const fmtFramePnl = (v) => (v == null ? '—' : moneyCompact(v, frameCcy, 100_000));

  const guard = (fn) => () => { if (!pending && fn) fn(pos); };
  const assetClick = guard(onAssetClick);
  const marketDetailClick = (e) => {
    e.stopPropagation();
    if (pending) return;
    navigate(marketHref(pos.assetType, pos.assetCode));
  };
  const marketLinkLabel = t('portfolio.positions.viewOnMarket', { defaultValue: 'Pazar detayı' });
  const editClick = isClosedDerivative ? guard(onCloseClick) : guard(onEditClick);
  const deleteClick = guard(onDeleteClick);
  const closeClick = guard(onCloseClick);
  const sellClick = guard(onSellClick);
  const reopenClick = guard(onReopenClick);

  return (
    <Card
      as={motion.div}
      variants={cardVariants}
      variant="elevated"
      radius="2xl"
      padding="none"
      backdropBlur
      interactive={!pending}
      pending={pending}
      className="group"
    >
      {pending && (
        <>
          <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-accent/60 to-transparent animate-pulse z-10" />
          <div className="absolute top-2.5 left-3 right-3 flex items-center justify-between text-[11px] font-medium text-accent z-10 pointer-events-none">
            <div className="flex items-center gap-1.5">
              <Spinner size="xs" tone="inherit" />
              <span className="tracking-tight">{t('portfolio.positions.preparing')}</span>
            </div>
            <motion.span
              key={elapsed}
              initial={{ opacity: 0.4, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
              className="font-mono tabular-nums text-accent/80"
            >
              {String(elapsed).padStart(2, '0')}s
            </motion.span>
          </div>
        </>
      )}
      <div className={pending ? 'pt-7 opacity-50' : ''}>
      <div className="hidden lg:grid lg:grid-cols-[28px_minmax(220px,2.4fr)_56px_92px_92px_72px_84px_84px_104px_112px_104px_24px] gap-3 items-start p-4 min-w-0">
        <div className="flex items-center justify-center pt-1">
          {!pending && <SelectableCheckbox checked={!!selected} onClick={onToggleSelect} label={pos.assetCode} />}
        </div>
        <div className="flex items-center gap-2.5 cursor-pointer min-w-0" onClick={assetClick}>
          <PositionAssetBadge pos={pos} />
          <div className="min-w-0">
            <div className="flex items-start gap-1.5 flex-wrap">
              <p className={`font-semibold text-fg leading-tight ${isDerivative ? 'text-xs break-all' : 'text-sm truncate'}`}>
                {assetCodeLabel(pos.assetType, pos.assetCode)}
              </p>
              <button
                type="button"
                onClick={marketDetailClick}
                title={marketLinkLabel}
                aria-label={marketLinkLabel}
                className="inline-flex items-center justify-center rounded-md p-0.5 text-accent/80 hover:text-accent hover:bg-accent/10 transition-all opacity-0 group-hover:opacity-100 focus:opacity-100 outline-none border-none bg-transparent cursor-pointer"
              >
                <ExternalLink className="h-3 w-3" />
              </button>
            </div>
            {!isDerivative && (
              <p className="text-[11px] text-fg-muted truncate">{displayName}</p>
            )}
            {isDerivative && derivativeName && (
              <p className="text-[11px] text-fg-muted truncate">{derivativeName}</p>
            )}
            {isDerivative && pos.derivative && (
              <PositionDerivativeChips meta={pos.derivative} money={money} t={t} localeTag={localeTag} entryDate={pos.entryDate} />
            )}
          </div>
        </div>
        <p className="text-left text-[11px] font-mono text-fg truncate">{Number(pos.quantity).toLocaleString(localeTag, { maximumFractionDigits: 6 })}</p>
        <p className="text-left text-[11px] font-mono text-fg-muted truncate">{formatEntryDate(pos.entryDate, localeTag)}</p>
        <p className="text-left text-[11px] font-mono text-fg-muted truncate">
          {isClosedSpot || isClosedDerivative ? formatEntryDate(pos.exitDate, localeTag) || '—' : '—'}
        </p>
        <div className="flex justify-start">
          <PositionStatusBadge closed={isClosedSpot || isClosedDerivative} isDerivative={isDerivative} />
        </div>
        <p className="text-left text-[11px] font-mono text-fg truncate" title={money(pos.entryPrice, 'TRY', { dateAt: pos.entryDate, natural: nativeCurrency })}>{money(pos.entryPrice, 'TRY', { dateAt: pos.entryDate, natural: nativeCurrency })}</p>
        <p className={`text-left text-[11px] font-mono truncate ${isClosedSpot || isClosedDerivative ? 'text-fg-muted italic' : 'text-fg'}`} title={money(pos.currentPriceTry, 'TRY', closedFx)}>{money(pos.currentPriceTry, 'TRY', closedFx)}</p>
        <p className={`text-left text-[11px] font-mono truncate ${isClosedSpot || isClosedDerivative ? 'text-fg-muted italic' : 'text-fg'}`} title={useFrame ? fmtFramePnl(costFrame + framePnl) : money(totalEquityTry, 'TRY', closedFx)}>{useFrame ? fmtFramePnl(costFrame + framePnl) : (isClosed ? money(totalEquityTry, 'TRY', closedFx) : bigMoney(totalEquityTry))}</p>
        <div className="text-left min-w-0">
          <p className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]} truncate`} title={useFrame ? fmtFramePnl(framePnl) : money(pos.pnlTry, 'TRY', closedFx)}>{useFrame ? fmtFramePnl(framePnl) : (isClosed ? money(pos.pnlTry, 'TRY', closedFx) : bigMoney(pos.pnlTry))}</p>
          <div className="flex items-center gap-1 flex-wrap">
            <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(shownPnlPct)}</span>
            {resolveTarget('TRY', nativeCurrency) === 'TRY' && pos.realPnlPercent != null && (
              <span className={`inline-flex items-center text-[9px] font-mono tabular-nums tracking-[0.04em] uppercase ${Number(pos.realPnlPercent) >= 0 ? 'text-emerald-500' : 'text-red-500'}`} title={t('portfolio.positions.realReturnTooltip')}>
                {t('portfolio.positions.realReturnAbbr')} {formatPercent(pos.realPnlPercent)}
              </span>
            )}
          </div>
        </div>
        <div className="flex justify-start gap-1">
          {showEdit && (
            <button onClick={(e) => { e.stopPropagation(); editClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label={t('common.edit')}>
              <Pencil className="h-3 w-3" />
            </button>
          )}
          {showSellButton && (
            <button onClick={(e) => { e.stopPropagation(); sellClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.sell.title', 'Sell')}>
              <ShoppingBag className="h-3 w-3" />
            </button>
          )}
          {showReopenButton && (
            <button onClick={(e) => { e.stopPropagation(); reopenClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.reopen.title', 'Reopen')}>
              <RotateCcw className="h-3 w-3" />
            </button>
          )}
          {showCloseButton && (
            <button onClick={(e) => { e.stopPropagation(); closeClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.derivatives.closeTitle', 'Pozisyon Kapat')}>
              <XCircle className="h-3 w-3" />
            </button>
          )}
          <button onClick={(e) => { e.stopPropagation(); deleteClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label={t('common.delete')}>
            <Trash2 className="h-3 w-3" />
          </button>
        </div>
        <ChevronRight onClick={assetClick} className="h-4 w-4 text-fg-muted group-hover:text-accent group-hover:translate-x-1 transition-all cursor-pointer" />
      </div>

      <div className="lg:hidden overflow-x-auto p-3 sm:p-4 space-y-3">
        <div className="flex items-start justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2.5 min-w-0 flex-1" onClick={assetClick}>
            {!pending && <SelectableCheckbox checked={!!selected} onClick={onToggleSelect} label={pos.assetCode} />}
            <PositionAssetBadge pos={pos} />
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 flex-wrap">
                <p className="text-sm font-semibold text-fg truncate max-w-[120px]">{pos.assetCode}</p>
                <button
                  type="button"
                  onClick={marketDetailClick}
                  title={marketLinkLabel}
                  aria-label={marketLinkLabel}
                  className="inline-flex items-center justify-center rounded-md p-0.5 text-accent/80 hover:text-accent hover:bg-accent/10 transition-all outline-none border-none bg-transparent cursor-pointer"
                >
                  <ExternalLink className="h-3 w-3" />
                </button>
                <PositionStatusBadge closed={isClosedSpot || isClosedDerivative} isDerivative={isDerivative} />
              </div>
              {!isDerivative && (
                <p className="text-[11px] text-fg-muted truncate">{displayName}</p>
              )}
              {isDerivative && derivativeName && (
                <p className="text-[11px] text-fg-muted truncate">{derivativeName}</p>
              )}
              {isDerivative && pos.derivative && (
                <PositionDerivativeChips meta={pos.derivative} money={money} t={t} localeTag={localeTag} entryDate={pos.entryDate} />
              )}
            </div>
          </div>
          <div className="flex items-center gap-1.5 flex-wrap justify-end">
            <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>{formatPercent(shownPnlPct)}</span>
            {showEdit && (
              <button onClick={(e) => { e.stopPropagation(); editClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label={t('common.edit')}>
                <Pencil className="h-3 w-3" />
              </button>
            )}
            {showSellButton && (
              <button onClick={(e) => { e.stopPropagation(); sellClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.sell.title', 'Sell')}>
                <ShoppingBag className="h-3 w-3" />
              </button>
            )}
            {showReopenButton && (
              <button onClick={(e) => { e.stopPropagation(); reopenClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.reopen.title', 'Reopen')}>
                <RotateCcw className="h-3 w-3" />
              </button>
            )}
            {showCloseButton && (
              <button onClick={(e) => { e.stopPropagation(); closeClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.derivatives.closeTitle', 'Pozisyon Kapat')}>
                <XCircle className="h-3 w-3" />
              </button>
            )}
            <button onClick={(e) => { e.stopPropagation(); deleteClick(); }} className="flex items-center justify-center w-8 h-8 sm:w-7 sm:h-7 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label={t('common.delete')}>
              <Trash2 className="h-3 w-3" />
            </button>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="rounded-lg bg-bg-base px-2.5 py-2 min-w-0"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.quantityCol')}</p><p className="font-mono text-fg font-medium truncate">{Number(pos.quantity).toLocaleString(localeTag, { maximumFractionDigits: 6 })}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2 min-w-0"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.entryDateCol')}</p><p className="font-mono text-fg font-medium truncate">{formatEntryDate(pos.entryDate, localeTag)}</p></div>
          {(isClosedSpot || isClosedDerivative) && (
            <div className="rounded-lg bg-bg-base px-2.5 py-2 min-w-0"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.exitDateLabel')}</p><p className="font-mono text-fg font-medium truncate">{formatEntryDate(pos.exitDate, localeTag) || '—'}</p></div>
          )}
          <div className="rounded-lg bg-bg-base px-2.5 py-2 min-w-0"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.entryPriceCol')}</p><p className="font-mono text-fg font-medium truncate" title={money(pos.entryPrice, 'TRY', { dateAt: pos.entryDate, natural: nativeCurrency })}>{money(pos.entryPrice, 'TRY', { dateAt: pos.entryDate, natural: nativeCurrency })}</p></div>
          <div className="rounded-lg bg-bg-base px-2.5 py-2 min-w-0"><p className="text-fg-muted mb-0.5">{t('portfolio.positions.pnlCol')}</p><p className={`font-mono font-semibold truncate ${changeColors[pnlClass]}`} title={useFrame ? fmtFramePnl(framePnl) : money(pos.pnlTry, 'TRY', closedFx)}>{useFrame ? fmtFramePnl(framePnl) : (isClosed ? money(pos.pnlTry, 'TRY', closedFx) : bigMoney(pos.pnlTry))}</p></div>
        </div>
      </div>
      </div>
    </Card>
  );
}
