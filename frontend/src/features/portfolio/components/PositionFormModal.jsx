import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Calendar, Hash, Tag, Wallet } from 'lucide-react';
import { Check, AlertCircle } from '../../../shared/components/feedback/AnimatedIcons';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import ProcessingSteps from '../../../shared/components/feedback/ProcessingSteps';
import { useMoney } from '../../../shared/hooks/useMoney';
import { assetCodeLabel } from '../../../shared/utils/assetCode';
import { usePositionForm } from '../hooks/usePositionForm';
import PositionFormConfirmPanel from './PositionFormConfirmPanel';
import PositionFormSuccessPanel from './PositionFormSuccessPanel';
import { todayInputValue, preventDecimal, describeAction } from '../lib/positionFormHelpers';
import { currencySymbolOf } from '../../../shared/utils/priceCurrency';
import { MAX_MONEY, PRICE_DECIMALS, sanitizeNumberInput } from '../../../shared/utils/numberInput';
import { commodityLabel } from '../../../shared/utils/commodityName';

export default function PositionFormModal({ mode, portfolioId, portfolioPicker, asset, position, onClose, onComplete }) {
  const { t } = useTranslation();
  const { format: money, formatCompact } = useMoney();
  const f = usePositionForm({ mode, portfolioId, asset, position, onClose, onComplete });
  const {
    target, isFractional, isEdit, inputCurrency, processingSteps, processingStep,
    form, error, phase, priceTouched, limits, highlightedDates, viewInitialLoading,
    entryLoading, dataAvailable, suggestedPriceDisplay,
    closeEnabled, exitDate, exitPrice, exitHighlights, exitLoading, totalCostTry,
    setViewMonth, setCloseEnabled, setExitDate, setExitPrice, setExitPriceTouched,
    setExitViewMonth, setError, setPhase,
    handleDateChange, handlePriceChange, handleQuantityChange, useSuggestedPrice,
    handleSubmit, handleConfirm,
  } = f;

  const displayCode = assetCodeLabel(target.assetType, target.assetCode);
  const dismissable = phase === 'form' || phase === 'confirm';

  return createPortal(
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-3 sm:p-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="absolute inset-0 modal-overlay backdrop-blur-sm"
        onClick={dismissable ? onClose : undefined}
      />
      <motion.div
        initial={{ opacity: 0, scale: 0.97 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.97 }}
        transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
        className="relative w-full max-w-sm max-h-[90dvh] overflow-y-auto rounded-2xl border border-border-default modal-panel p-4 sm:p-6"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10">
              <Wallet className="h-4 w-4 text-accent" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-fg">
                {isEdit ? t('positionForm.titleEdit') : t('positionForm.titleAdd')}
              </h2>
              <p className="text-xs text-fg-muted">{commodityLabel(t, target.assetType, target.assetCode, target.assetName || displayCode)}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={!dismissable}
            className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {phase === 'success' && (
          <PositionFormSuccessPanel
            title={isEdit ? t('positionForm.success.titleEdit') : t('positionForm.success.titleAdd')}
            subtitle={describeAction(t, isEdit, form, displayCode, isFractional)}
          />
        )}

        {phase === 'processing' && <ProcessingSteps steps={processingSteps} currentStep={processingStep} />}

        {phase === 'confirm' && (
          <PositionFormConfirmPanel
            isEdit={isEdit}
            displayCode={displayCode}
            form={form}
            isFractional={isFractional}
            totalCostTry={totalCostTry}
            inputCurrency={inputCurrency}
            closeEnabled={closeEnabled}
            exitDate={exitDate}
            exitPrice={exitPrice}
            onCancel={() => setPhase('form')}
            onConfirm={handleConfirm}
          />
        )}

        {phase === 'form' && (
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {portfolioPicker}
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Calendar className="h-3 w-3" />
                {t('positionForm.fields.entryDate')}
              </label>
              <DatePickerPopover
                value={form.entryDate}
                onChange={handleDateChange}
                onMonthChange={(y, m) => setViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
                minDate={limits?.minEntryDate}
                maxDate={limits?.maxEntryDate || todayInputValue()}
                highlightedDates={highlightedDates}
                loading={viewInitialLoading}
              />
              <DataAvailabilityHint
                dataAvailable={dataAvailable}
                loading={entryLoading}
                suggestedPrice={suggestedPriceDisplay}
                inputCurrency={inputCurrency}
                onApply={useSuggestedPrice}
                applied={!priceTouched && suggestedPriceDisplay != null && Number(form.entryPrice) === suggestedPriceDisplay}
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
                <span className="inline-flex items-center gap-1.5">
                  <Tag className="h-3 w-3" />
                  {t('positionForm.fields.entryPrice')}
                </span>
                <span className="font-mono text-[10px] uppercase tracking-wider text-accent">
                  {inputCurrency} ({currencySymbolOf(inputCurrency)})
                </span>
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle font-mono text-sm pointer-events-none">
                  {currencySymbolOf(inputCurrency)}
                </span>
                <input
                  type="number"
                  step="any"
                  min="0"
                  max="1000000000000"
                  inputMode="decimal"
                  value={form.entryPrice}
                  onChange={handlePriceChange}
                  placeholder="0.00"
                  className="w-full rounded-lg border border-border-default bg-bg-base pl-7 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Hash className="h-3 w-3" />
                {isFractional ? t('positionForm.fields.quantity') : t('positionForm.fields.quantityShares')}
              </label>
              <input
                type="number"
                step={isFractional ? 'any' : '1'}
                min="0"
                max="1000000000"
                inputMode={isFractional ? 'decimal' : 'numeric'}
                value={form.quantity}
                onChange={handleQuantityChange}
                onKeyDown={isFractional ? undefined : preventDecimal}
                placeholder={isFractional ? '0.00' : t('positionForm.fields.minOne')}
                autoFocus
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>

            {!isEdit && (
              <div className="rounded-lg border border-border-default bg-bg-base/60">
                <button
                  type="button"
                  onClick={() => setCloseEnabled((v) => !v)}
                  className="w-full flex items-center justify-between px-3 py-2 text-xs font-medium text-fg-muted hover:text-fg cursor-pointer bg-transparent border-none"
                >
                  <span>{t('positionForm.alreadySoldToggle', { defaultValue: 'Bu pozisyonu zaten sattım' })}</span>
                  <span className={`text-[10px] ${closeEnabled ? 'text-accent' : ''}`}>{closeEnabled ? '−' : '+'}</span>
                </button>
                {closeEnabled && (
                  <div className="space-y-3 px-3 pb-3">
                    <div className="space-y-1.5">
                      <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                        <Calendar className="h-3 w-3" />
                        {t('positionForm.fields.exitDate', { defaultValue: 'Çıkış tarihi' })}
                      </label>
                      <DatePickerPopover
                        value={exitDate}
                        onChange={(iso) => { setExitDate(iso); setExitPriceTouched(false); setError(null); }}
                        onMonthChange={(y, m) => setExitViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
                        minDate={form.entryDate}
                        maxDate={todayInputValue()}
                        highlightedDates={exitHighlights}
                        loading={exitLoading}
                      />
                    </div>
                    <div className="space-y-1.5">
                      <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
                        <span className="inline-flex items-center gap-1.5">
                          <Tag className="h-3 w-3" />
                          {t('positionForm.fields.exitPrice', { defaultValue: 'Çıkış fiyatı' })}
                        </span>
                        <span className="font-mono text-[10px] uppercase tracking-wider text-accent">
                          {inputCurrency} ({currencySymbolOf(inputCurrency)})
                        </span>
                      </label>
                      <div className="relative">
                        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle font-mono text-sm pointer-events-none">
                          {currencySymbolOf(inputCurrency)}
                        </span>
                        <input
                          type="number"
                          step="any"
                          min="0"
                          max="1000000000000"
                          inputMode="decimal"
                          value={exitPrice}
                          onChange={(e) => { setExitPrice(sanitizeNumberInput(e.target.value, MAX_MONEY, PRICE_DECIMALS)); setExitPriceTouched(true); setError(null); }}
                          placeholder="0.00"
                          className="w-full rounded-lg border border-border-default bg-bg-base pl-7 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {totalCostTry != null && (
              <div className="rounded-xl border border-accent/30 bg-gradient-to-r from-accent/5 to-transparent px-4 py-3 flex items-center justify-between gap-3 min-w-0">
                <div className="flex flex-col gap-0.5 min-w-0">
                  <span className="text-xs font-semibold text-accent">{t('positionForm.totalCost')}</span>
                  <span className="text-[10px] font-mono uppercase tracking-wider text-fg-subtle">
                    {t('positionForm.storedAsTry', { defaultValue: 'TRY olarak kaydedilir' })}
                  </span>
                </div>
                <span
                  className="text-lg font-bold font-mono text-accent truncate"
                  title={money(totalCostTry, 'TRY', { natural: inputCurrency, dateAt: form.entryDate })}
                >
                  {formatCompact(totalCostTry, 'TRY', 1_000_000_000, inputCurrency, form.entryDate)}
                </span>
              </div>
            )}

            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="flex items-center gap-2 text-xs text-danger bg-danger/5 rounded-lg px-3 py-2 border border-danger/20"
                >
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  {error}
                </motion.div>
              )}
            </AnimatePresence>

            <button
              type="submit"
              className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
            >
              <Wallet className="h-4 w-4" />
              {isEdit ? t('positionForm.continue') : t('positionForm.titleAdd')}
            </button>
          </form>
        )}
      </motion.div>
    </div>,
    document.body,
  );
}

function DataAvailabilityHint({ dataAvailable, suggestedPrice, inputCurrency, onApply, applied, loading }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  if (loading) {
    return <div className="h-[30px] rounded-md border border-border-default/40 bg-surface/20 animate-pulse" />;
  }
  if (dataAvailable) {
    return (
      <div className="flex items-center justify-between gap-2 text-[11px] text-success bg-success/5 rounded-md px-2.5 py-1.5 border border-success/20">
        <div className="flex items-center gap-1.5">
          <Check className="h-3 w-3 shrink-0" />
          <span dangerouslySetInnerHTML={{
            __html: t('positionForm.availability.has', { price: money(suggestedPrice, inputCurrency) }),
          }} />
        </div>
        {!applied && (
          <button
            type="button"
            onClick={onApply}
            className="text-[10px] font-semibold text-success hover:underline bg-transparent border-none cursor-pointer"
          >
            {t('positionForm.availability.apply')}
          </button>
        )}
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1.5 text-[11px] text-warning bg-warning/5 rounded-md px-2.5 py-1.5 border border-warning/20">
      <AlertCircle className="h-3 w-3 shrink-0" />
      <span>{t('positionForm.availability.none')}</span>
    </div>
  );
}
