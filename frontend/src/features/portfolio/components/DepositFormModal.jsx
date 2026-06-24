import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Calendar, Percent, Landmark } from 'lucide-react';
import { AlertCircle } from '../../../shared/components/feedback/AnimatedIcons';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import ProcessingSteps from '../../../shared/components/feedback/ProcessingSteps';
import DepositConfirmPanel from './depositForm/DepositConfirmPanel';
import DepositCurrencySelector from './depositForm/DepositCurrencySelector';
import DepositTypeSelect from './depositForm/DepositTypeSelect';
import DepositInterestBreakdown from './depositForm/DepositInterestBreakdown';
import {
  useDepositForm, CURRENCIES, CUSTOM_RATE_KEY, MIN_DEPOSIT_START, MAX_DEPOSIT_TERM_YEARS, plusYearsInput,
} from '../hooks/useDepositForm';
import { todayInputValue } from '../lib/positionFormHelpers';
import {
  MAX_MONEY, MAX_PERCENT, PRICE_DECIMALS, clampNumberInput, sanitizeNumberInput,
} from '../../../shared/utils/numberInput';

export default function DepositFormModal({ mode = 'add', portfolioId, portfolioPicker, deposit, onClose, onComplete }) {
  const {
    t, isEdit, currency, setCurrency, principal, setPrincipal, setAnnualRate, indicatorCode, setIndicatorCode,
    startDate, maturityDate, setMaturityDate, withholdingPct, setWithholdingPct, rateMenuOpen, setRateMenuOpen,
    setRateTouched, phase, setPhase, error, setError, processingStep, processingSteps, depositRates, ratesLoading,
    selectedRate, suggestedRate, rateValue, breakdown, handleRateSelect, dismissable, sym, handleSubmit,
    handleConfirm, handleStartDateChange, localeTag, startDisplay, maturityDisplay,
  } = useDepositForm({ mode, portfolioId, deposit, onClose, onComplete });

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
        className="relative w-full max-w-sm sm:max-w-2xl max-h-[90dvh] flex flex-col rounded-2xl border border-border-default modal-panel"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
        <div aria-hidden className="pointer-events-none absolute -top-16 -right-10 h-40 w-40 rounded-full bg-accent/15 blur-[80px] opacity-60" />
        <div aria-hidden className="pointer-events-none absolute -bottom-20 -left-12 h-40 w-40 rounded-full bg-success/10 blur-[80px] opacity-50" />
        <div className="flex items-center justify-between px-4 sm:px-6 pt-4 sm:pt-6 pb-4 shrink-0">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10">
              <Landmark className="h-4 w-4 text-accent" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-fg">
                {isEdit ? t('deposits.form.titleEdit') : t('deposits.form.titleAdd')}
              </h2>
              <p className="text-xs text-fg-muted">{t('deposits.form.subtitle')}</p>
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

        <div className="flex-1 min-h-0 overflow-y-auto px-4 sm:px-6 pb-4 sm:pb-6">
        {phase === 'processing' && <ProcessingSteps steps={processingSteps} currentStep={processingStep} />}

        {phase === 'confirm' && (
          <DepositConfirmPanel
            sym={sym}
            principal={principal}
            localeTag={localeTag}
            currency={currency}
            rateValue={rateValue}
            selectedRate={selectedRate}
            startDisplay={startDisplay}
            maturityDisplay={maturityDisplay}
            onCancel={() => setPhase('form')}
            onConfirm={handleConfirm}
          />
        )}

        {phase === 'form' && (
          <form onSubmit={handleSubmit} noValidate className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {portfolioPicker && <div className="sm:col-span-2">{portfolioPicker}</div>}

            <DepositCurrencySelector
              currencies={CURRENCIES}
              currency={currency}
              onSelect={(c) => { setCurrency(c); setIndicatorCode(''); setError(null); }}
            />

            <DepositTypeSelect
              ratesLoading={ratesLoading}
              rateMenuOpen={rateMenuOpen}
              setRateMenuOpen={setRateMenuOpen}
              selectedRate={selectedRate}
              localeTag={localeTag}
              depositRates={depositRates}
              indicatorCode={indicatorCode}
              onRateSelect={handleRateSelect}
              customRateKey={CUSTOM_RATE_KEY}
            />

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
                <span className="inline-flex items-center gap-1.5">
                  <Landmark className="h-3 w-3" />
                  {t('deposits.fields.principal')}
                </span>
                <span className="font-mono text-[10px] uppercase tracking-wider text-accent">{currency} ({sym})</span>
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle font-mono text-sm pointer-events-none">{sym}</span>
                <input
                  type="number"
                  step="any"
                  min="0"
                  max={MAX_MONEY}
                  inputMode="decimal"
                  value={principal}
                  onChange={(e) => { setPrincipal(sanitizeNumberInput(e.target.value, MAX_MONEY, PRICE_DECIMALS)); setError(null); }}
                  placeholder="0.00"
                  autoFocus
                  className="w-full rounded-lg border border-border-default bg-bg-base pl-7 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Percent className="h-3 w-3" />
                {t('deposits.fields.annualRate')}
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle font-mono text-sm pointer-events-none">%</span>
                <input
                  type="number"
                  step="any"
                  min="0"
                  max={MAX_PERCENT}
                  inputMode="decimal"
                  value={rateValue}
                  onChange={(e) => { setAnnualRate(clampNumberInput(e.target.value, MAX_PERCENT)); setRateTouched(true); setError(null); }}
                  placeholder="0.00"
                  className="w-full rounded-lg border border-border-default bg-bg-base pl-7 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                />
              </div>
              {indicatorCode && suggestedRate != null && (
                <div className="flex items-center gap-1.5 rounded-lg border border-accent/20 bg-accent/5 px-2.5 py-1.5 text-[11px] text-fg-muted">
                  <Percent className="h-3 w-3 text-accent shrink-0" />
                  {t('deposits.fields.rateOnDate', {
                    date: startDisplay,
                    rate: suggestedRate.toLocaleString(localeTag, { maximumFractionDigits: 2 }),
                  })}
                </div>
              )}
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Calendar className="h-3 w-3" />
                {t('deposits.fields.startDate')}
              </label>
              <DatePickerPopover
                value={startDate}
                onChange={handleStartDateChange}
                minDate={MIN_DEPOSIT_START}
                maxDate={todayInputValue()}
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Calendar className="h-3 w-3" />
                {t('deposits.fields.maturityDate')}
              </label>
              <DatePickerPopover
                value={maturityDate}
                onChange={(iso) => { setMaturityDate(iso); setError(null); }}
                minDate={startDate}
                maxDate={startDate ? plusYearsInput(startDate, MAX_DEPOSIT_TERM_YEARS) : undefined}
              />
            </div>

            {/* Stopaj (withholding) — user-editable; prefilled with the default, varies by term/decree */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
                <span className="inline-flex items-center gap-1.5">
                  <Percent className="h-3 w-3" />
                  {t('deposits.fields.withholding')}
                </span>
                <span className="text-[10px] text-fg-subtle">{t('deposits.fields.withholdingHint')}</span>
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle font-mono text-sm pointer-events-none">%</span>
                <input
                  type="number"
                  step="any"
                  min="0"
                  max="100"
                  inputMode="decimal"
                  value={withholdingPct}
                  onChange={(e) => { setWithholdingPct(clampNumberInput(e.target.value, 100)); setError(null); }}
                  placeholder="15"
                  className="w-full rounded-lg border border-border-default bg-bg-base pl-7 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                />
              </div>
            </div>

            {/* Live interest breakdown so the holder sees the stopaj that will be deducted + the net they receive */}
            <DepositInterestBreakdown
              breakdown={breakdown}
              sym={sym}
              localeTag={localeTag}
              withholdingPct={withholdingPct}
            />

            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="sm:col-span-2 flex items-center gap-2 text-xs text-danger bg-danger/5 rounded-lg px-3 py-2 border border-danger/20"
                >
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  {error}
                </motion.div>
              )}
            </AnimatePresence>

            <button
              type="submit"
              className="sm:col-span-2 w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
            >
              <Landmark className="h-4 w-4" />
              {isEdit ? t('deposits.form.continue') : t('deposits.form.titleAdd')}
            </button>
          </form>
        )}
        </div>
      </motion.div>
    </div>,
    document.body,
  );
}
