import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Calendar, Percent, Landmark, Tag, Wallet, ChevronDown, Check } from 'lucide-react';
import { AlertTriangle, AlertCircle } from '../../../shared/components/feedback/AnimatedIcons';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import ProcessingSteps from '../../../shared/components/feedback/ProcessingSteps';
import PositionFormSuccessPanel from './PositionFormSuccessPanel';
import useProcessingAnimation from '../../../shared/hooks/useProcessingAnimation';
import { useAddDeposit, useUpdateDeposit } from '../hooks/useFixedIncomePositions';
import { useDepositRates } from '../hooks/useDepositRates';
import { useMacroIndicatorHistory } from '../../macro/hooks/useMacroIndicators';
import { extractApiError } from '../../../shared/utils/apiError';
import { currencySymbolOf } from '../../../shared/utils/priceCurrency';
import { currentLocaleTag } from '../../../shared/utils/formatters';
import {
  PROCESSING_STEP_DEFS, SUCCESS_HOLD_MS, todayInputValue, isoToDateInput,
} from '../lib/positionFormHelpers';
import {
  MAX_MONEY, MAX_PERCENT, PRICE_DECIMALS, clampNumberInput, sanitizeNumberInput, toInputValue,
} from '../../../shared/utils/numberInput';

const CURRENCIES = ['TRY', 'USD', 'EUR'];
// Mirror DepositValidator.MIN_START_DATE / MAX_TERM_YEARS so out-of-range dates are caught client-side.
const MIN_DEPOSIT_START = '2000-01-04';
const MAX_DEPOSIT_TERM_YEARS = 30;
// Prefill matching the backend default (PortfolioProperties.deposit.withholdingTaxRate = 0.15). Türkiye deposit
// stopaj varies by term/decree, so it is only a starting suggestion the holder can change.
const DEFAULT_STOPAJ_PCT = 15;

function plusYearsInput(dateInput, years) {
  const d = new Date(`${dateInput}T00:00:00`);
  d.setFullYear(d.getFullYear() + years);
  return d.toISOString().slice(0, 10);
}

function plusMonthsInput(dateInput, months) {
  const d = new Date(`${dateInput}T00:00:00`);
  d.setMonth(d.getMonth() + months);
  return d.toISOString().slice(0, 10);
}

const CUSTOM_RATE_KEY = '__custom__';

/**
 * Forward-filled published rate on or before {@code isoDate} from a macro deposit-rate series (chronological
 * asc): the rate quoted on the chosen start day, carrying the last publication over a gap. Falls back to the
 * earliest known value when the start date predates the series. Null when nothing is priced.
 */
function rateOnOrBefore(history, isoDate) {
  if (!isoDate || !Array.isArray(history) || history.length === 0) return null;
  const day = String(isoDate).slice(0, 10);
  let chosen = null;
  for (const h of history) {
    if (h?.value == null) continue;
    const d = String(h.date ?? h.observedAt ?? '').slice(0, 10);
    if (!d) continue;
    if (d <= day) chosen = Number(h.value);
    else break;
  }
  if (chosen == null) {
    const first = history.find((h) => h?.value != null);
    if (first) chosen = Number(first.value);
  }
  return Number.isFinite(chosen) ? chosen : null;
}

function rateToInput(value) {
  return value == null ? '' : String(Number(Number(value).toFixed(4)));
}

function ConfirmRow({ label, value }) {
  return (
    <div className="flex items-center justify-between gap-2 text-xs">
      <span className="text-fg-muted shrink-0">{label}</span>
      <span className="font-mono font-medium text-fg text-right break-all min-w-0">{value}</span>
    </div>
  );
}

export default function DepositFormModal({ mode = 'add', portfolioId, portfolioPicker, deposit, onClose, onComplete }) {
  const { t } = useTranslation();
  const isEdit = mode === 'edit';

  const [currency, setCurrency] = useState(deposit?.currency || 'TRY');
  const [principal, setPrincipal] = useState(deposit?.principal != null ? toInputValue(deposit.principal) : '');
  const [annualRate, setAnnualRate] = useState(deposit?.annualRate != null ? String(deposit.annualRate) : '');
  const [indicatorCode, setIndicatorCode] = useState(deposit?.indicatorCode || '');
  const [startDate, setStartDate] = useState(deposit?.startDate ? isoToDateInput(deposit.startDate) : todayInputValue());
  const [maturityDate, setMaturityDate] = useState(deposit?.maturityDate ? isoToDateInput(deposit.maturityDate) : '');
  // Stopaj entered as a PERCENT (e.g. 15 for 15%); sent as a fraction. Prefilled from the holding on edit, else
  // the default suggestion.
  const [withholdingPct, setWithholdingPct] = useState(
    deposit?.withholdingRate != null ? String(Number(deposit.withholdingRate) * 100) : String(DEFAULT_STOPAJ_PCT),
  );
  const [rateMenuOpen, setRateMenuOpen] = useState(false);
  // Once the user types a rate (or picks "Özel"), stop auto-suggesting; reset whenever the deposit type or start
  // date changes so the chosen term's rate ON the selected start date re-seeds the field.
  const [rateTouched, setRateTouched] = useState(isEdit);

  const [phase, setPhase] = useState('form');
  const [error, setError] = useState(null);

  const { processingStep, runAnimation, reset: resetProcessing } = useProcessingAnimation();
  const processingSteps = useMemo(
    () => PROCESSING_STEP_DEFS.map((s) => ({ label: t(`positionForm.steps.${s.labelKey}`), duration: s.duration })),
    [t],
  );

  const addMutation = useAddDeposit(portfolioId);
  const updateMutation = useUpdateDeposit(portfolioId);

  const { rates: depositRates, isLoading: ratesLoading } = useDepositRates(currency);

  // Resolve the active select option: an indicatorCode that matches a published
  // rate selects that term; anything else (including manual entry) is "custom".
  const selectedRate = useMemo(
    () => depositRates.find((r) => r.code === indicatorCode) || null,
    [depositRates, indicatorCode],
  );

  // History of the selected term's published rate, so the suggestion reflects the rate ON the chosen start date —
  // deposit rates drift over time, so a back-dated start may carry a different rate than today's headline. NB: the
  // window must reach BEFORE startDate (no `from` floor) — otherwise rateOnOrBefore can't see the rate published
  // prior to a back-dated start and wrongly falls back to the earliest fetched (post-start) row.
  const { data: rateHistory = [] } = useMacroIndicatorHistory(indicatorCode || undefined, {
    to: todayInputValue(),
  });
  const suggestedRate = useMemo(() => {
    if (!indicatorCode) return null;
    const onDate = rateOnOrBefore(rateHistory, startDate);
    if (onDate != null) return onDate;
    return selectedRate ? selectedRate.lastValue : null;
  }, [indicatorCode, rateHistory, startDate, selectedRate]);
  // The rate shown/submitted: the user's typed value once they take the wheel, otherwise the start-date
  // suggestion derived during render (no effect — the suggestion is never copied into state).
  const rateValue = rateTouched
    ? annualRate
    : (suggestedRate != null ? rateToInput(suggestedRate) : annualRate);

  // Live interest breakdown the user sees BEFORE committing: simple interest over the term (act/365), the stopaj
  // deducted from it, and the net + maturity value — mirroring DepositAccrualService so the form preview matches
  // what the backend will persist.
  const breakdown = useMemo(() => {
    const p = Number(principal);
    const r = Number(rateValue);
    const wp = Number(withholdingPct);
    if (!(p > 0) || !(r >= 0) || !startDate || !maturityDate) return null;
    const days = Math.max(0, Math.round((new Date(`${maturityDate}T00:00:00`) - new Date(`${startDate}T00:00:00`)) / 86400000));
    const gross = p * (r / 100) * (days / 365);
    const stopaj = gross * (Number.isFinite(wp) ? Math.min(Math.max(wp, 0), 100) / 100 : 0);
    const net = gross - stopaj;
    return { gross, stopaj, net, maturityValue: p + net };
  }, [principal, rateValue, withholdingPct, startDate, maturityDate]);

  const handleRateSelect = (key) => {
    setError(null);
    setRateMenuOpen(false);
    if (key === CUSTOM_RATE_KEY) {
      // Seed the editable field with the current effective rate so switching to "Özel" never blanks it.
      if (suggestedRate != null) setAnnualRate(rateToInput(suggestedRate));
      setIndicatorCode('');
      setRateTouched(true);
      return;
    }
    const rate = depositRates.find((r) => r.code === key);
    if (!rate) return;
    setIndicatorCode(rate.code);
    // Let the derived rateValue re-seed from the start-date suggestion for the new term.
    setRateTouched(false);
    // Suggest a maturity from the chosen term; the open-ended M12_PLUS bucket has no defined term, so its
    // maturity is left for manual entry.
    if (rate.termMonths && startDate) {
      setMaturityDate(plusMonthsInput(startDate, rate.termMonths));
    }
  };

  const dismissable = phase === 'form' || phase === 'confirm';
  const sym = currencySymbolOf(currency);

  const validate = () => {
    const principalNum = Number(principal);
    if (!principal || !(principalNum > 0) || principalNum > MAX_MONEY) return t('deposits.errors.principalInvalid');
    if (!CURRENCIES.includes(currency)) return t('deposits.errors.currencyRequired');
    const rateNum = Number(rateValue);
    if (rateValue === '' || !(rateNum >= 0) || rateNum > MAX_PERCENT) return t('deposits.errors.rateInvalid');
    const wp = Number(withholdingPct);
    if (withholdingPct !== '' && (!(wp >= 0) || wp > 100)) return t('deposits.errors.withholdingInvalid');
    if (!startDate) return t('deposits.errors.startRequired');
    if (startDate < MIN_DEPOSIT_START) return t('deposits.errors.startTooOld');
    if (!maturityDate) return t('deposits.errors.maturityRequired');
    if (new Date(maturityDate) <= new Date(startDate)) return t('deposits.errors.maturityAfterStart');
    if (maturityDate > plusYearsInput(startDate, MAX_DEPOSIT_TERM_YEARS)) return t('deposits.errors.maturityTooFar');
    return null;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setPhase('confirm');
  };

  const handleConfirm = async () => {
    setError(null);
    setPhase('processing');
    const payload = {
      currency,
      principal: Number(principal),
      annualRate: Number(rateValue),
      // Stopaj entered as a percent → sent as a fraction (null when blank, so the backend uses its default).
      withholdingRate: withholdingPct === '' ? null : Number(withholdingPct) / 100,
      ...(indicatorCode.trim() ? { indicatorCode: indicatorCode.trim() } : {}),
      // The backend deposit DTO is LocalDate (yyyy-MM-dd); send the plain picker value, NOT an ISO datetime
      // (dateInputToIso adds a T...Z that Jackson's ISO_LOCAL_DATE parser rejects with a 400).
      startDate: startDate.slice(0, 10),
      maturityDate: maturityDate.slice(0, 10),
    };
    const mutate = isEdit
      ? () => updateMutation.mutateAsync({ depositId: deposit.id, ...payload })
      : () => addMutation.mutateAsync(payload);
    try {
      await Promise.all([mutate(), runAnimation(processingSteps)]);
      setPhase('success');
      setTimeout(() => { onComplete?.(); onClose(); }, SUCCESS_HOLD_MS);
    } catch (err) {
      resetProcessing();
      setError(extractApiError(err, isEdit ? t('deposits.errors.updateFailed') : t('deposits.errors.addFailed')));
      setPhase('form');
    }
  };

  const localeTag = currentLocaleTag();
  const startDisplay = startDate ? new Date(startDate).toLocaleDateString(localeTag, { day: '2-digit', month: 'long', year: 'numeric' }) : '—';
  const maturityDisplay = maturityDate ? new Date(maturityDate).toLocaleDateString(localeTag, { day: '2-digit', month: 'long', year: 'numeric' }) : '—';

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
        {phase === 'success' && (
          <PositionFormSuccessPanel
            title={isEdit ? t('deposits.form.success.titleEdit') : t('deposits.form.success.titleAdd')}
            subtitle={t('deposits.form.success.subtitle')}
          />
        )}

        {phase === 'processing' && <ProcessingSteps steps={processingSteps} currentStep={processingStep} />}

        {phase === 'confirm' && (
          <motion.div initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} className="space-y-5 py-2">
            <div className="flex flex-col items-center gap-3">
              <div className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10">
                <AlertTriangle className="h-6 w-6 text-warning" />
              </div>
              <div className="text-center space-y-1">
                <p className="text-sm font-semibold text-fg">{t('deposits.form.confirm.heading')}</p>
                <p className="text-xs text-fg-muted">{t('deposits.form.confirm.sub')}</p>
              </div>
            </div>
            <div className="rounded-xl border border-border-default bg-bg-base px-4 py-3 space-y-2">
              <ConfirmRow label={t('deposits.fields.principal')} value={`${sym}${Number(principal).toLocaleString(localeTag)} ${currency}`} />
              <ConfirmRow label={t('deposits.fields.annualRate')} value={`%${Number(rateValue).toLocaleString(localeTag)}`} />
              {selectedRate && <ConfirmRow label={t('deposits.fields.depositType')} value={t(`marketOverview.macro.maturity${selectedRate.maturity}`)} />}
              <div className="border-t border-border-default pt-2 space-y-2">
                <ConfirmRow label={t('deposits.fields.startDate')} value={startDisplay} />
                <ConfirmRow label={t('deposits.fields.maturityDate')} value={maturityDisplay} />
              </div>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setPhase('form')}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={handleConfirm}
                className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
              >
                <Wallet className="h-4 w-4" />
                {t('common.confirm')}
              </button>
            </div>
          </motion.div>
        )}

        {phase === 'form' && (
          <form onSubmit={handleSubmit} noValidate className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {portfolioPicker && <div className="sm:col-span-2">{portfolioPicker}</div>}

            <div className="space-y-1.5 sm:col-span-2">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Wallet className="h-3 w-3" />
                {t('deposits.fields.currency')}
              </label>
              <div className="grid grid-cols-3 gap-1.5">
                {CURRENCIES.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => { setCurrency(c); setIndicatorCode(''); setError(null); }}
                    className={`flex items-center justify-center gap-1 rounded-lg py-2 text-xs font-semibold border transition-all cursor-pointer ${
                      currency === c
                        ? 'border-accent/40 bg-accent/10 text-accent'
                        : 'border-border-default bg-bg-base text-fg-muted hover:text-fg'
                    }`}
                  >
                    {currencySymbolOf(c)} {c}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-1.5 sm:col-span-2">
              <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
                <span className="inline-flex items-center gap-1.5">
                  <Tag className="h-3 w-3" />
                  {t('deposits.fields.depositType')}
                </span>
                {ratesLoading && <span className="text-[10px] text-fg-subtle">{t('common.loading')}</span>}
              </label>
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setRateMenuOpen((o) => !o)}
                  aria-haspopup="listbox"
                  aria-expanded={rateMenuOpen}
                  className={`w-full flex items-center justify-between gap-2 rounded-lg border bg-bg-base px-3 py-2.5 text-sm outline-none transition-all cursor-pointer ${
                    rateMenuOpen ? 'border-accent/50 ring-1 ring-accent/30' : 'border-border-default hover:border-accent/30'
                  }`}
                >
                  <span className="inline-flex items-center gap-2 min-w-0 truncate">
                    {selectedRate ? (
                      <>
                        <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md bg-accent/15 text-[10px] font-bold text-accent">
                          {t(`marketOverview.macro.maturity${selectedRate.maturity}`).replace(/\s/g, '').slice(0, 3)}
                        </span>
                        <span className="text-fg truncate">{t(`marketOverview.macro.maturity${selectedRate.maturity}`)}</span>
                        <span className="font-mono text-accent">%{selectedRate.lastValue.toLocaleString(localeTag)}</span>
                      </>
                    ) : (
                      <span className="text-fg-muted">{t('deposits.fields.depositTypeCustom')}</span>
                    )}
                  </span>
                  <ChevronDown className={`h-4 w-4 shrink-0 transition-transform ${rateMenuOpen ? 'rotate-180 text-accent' : 'text-fg-subtle'}`} />
                </button>
                <AnimatePresence>
                  {rateMenuOpen && (
                    <>
                      <div className="fixed inset-0 z-[1]" onClick={() => setRateMenuOpen(false)} aria-hidden />
                      <motion.div
                        initial={{ opacity: 0, y: -4 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -4 }}
                        transition={{ duration: 0.14 }}
                        role="listbox"
                        style={{ background: 'var(--color-bg-deep)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)' }}
                        className="absolute z-20 mt-1.5 w-full overflow-hidden rounded-xl border border-border-default p-1 shadow-2xl shadow-black/40 ring-1 ring-black/5"
                      >
                        {depositRates.map((r) => {
                          const active = r.code === indicatorCode;
                          return (
                            <button
                              key={r.code}
                              type="button"
                              onClick={() => handleRateSelect(r.code)}
                              className={`w-full flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-sm transition-colors border-none cursor-pointer ${
                                active ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg hover:bg-surface'
                              }`}
                            >
                              <span className="inline-flex items-center gap-2 min-w-0 truncate">
                                {active ? <Check className="h-3.5 w-3.5 shrink-0" /> : <span className="w-3.5 shrink-0" />}
                                {t(`marketOverview.macro.maturity${r.maturity}`)}
                              </span>
                              <span className={`font-mono text-xs ${active ? 'text-accent' : 'text-fg-muted'}`}>
                                %{r.lastValue.toLocaleString(localeTag)}
                              </span>
                            </button>
                          );
                        })}
                        <button
                          type="button"
                          onClick={() => handleRateSelect(CUSTOM_RATE_KEY)}
                          className={`w-full flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors border-none cursor-pointer ${
                            !selectedRate ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg hover:bg-surface'
                          }`}
                        >
                          {!selectedRate ? <Check className="h-3.5 w-3.5 shrink-0" /> : <span className="w-3.5 shrink-0" />}
                          {t('deposits.fields.depositTypeCustom')}
                        </button>
                      </motion.div>
                    </>
                  )}
                </AnimatePresence>
              </div>
              {!ratesLoading && depositRates.length === 0 && (
                <p className="text-[10px] text-fg-subtle">{t('deposits.fields.depositTypeNoRates')}</p>
              )}
            </div>

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
                onChange={(iso) => {
                  setStartDate(iso);
                  // Re-seed the rate from the chosen term's quote on the NEW start date, and shift the suggested
                  // maturity to keep the term length.
                  setRateTouched(false);
                  if (selectedRate?.termMonths) setMaturityDate(plusMonthsInput(iso, selectedRate.termMonths));
                  setError(null);
                }}
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
            {breakdown && breakdown.gross > 0 && (
              <div className="sm:col-span-2 rounded-xl border border-accent/25 bg-gradient-to-br from-accent/5 to-transparent px-4 py-3 space-y-1.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-fg-muted">{t('deposits.interest.gross')}</span>
                  <span className="font-mono text-fg">{sym}{breakdown.gross.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-fg-muted">{t('deposits.interest.stopaj')} (%{Number(withholdingPct) || 0})</span>
                  <span className="font-mono text-danger">−{sym}{breakdown.stopaj.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
                </div>
                <div className="flex items-center justify-between text-xs border-t border-border-default/50 pt-1.5">
                  <span className="font-medium text-fg">{t('deposits.interest.net')}</span>
                  <span className="font-mono font-semibold text-success">{sym}{breakdown.net.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-fg-muted">{t('deposits.interest.maturityValue')}</span>
                  <span className="font-mono font-semibold text-accent">{sym}{breakdown.maturityValue.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
                </div>
              </div>
            )}

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
