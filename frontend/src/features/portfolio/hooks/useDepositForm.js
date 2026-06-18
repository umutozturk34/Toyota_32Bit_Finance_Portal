import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from '../../../shared/components/feedback/toastBus';
import useProcessingAnimation from '../../../shared/hooks/useProcessingAnimation';
import { useAddDeposit, useUpdateDeposit } from './useFixedIncomePositions';
import { useDepositRates } from './useDepositRates';
import { useMacroIndicatorHistory } from '../../macro/hooks/useMacroIndicators';
import { extractApiError } from '../../../shared/utils/apiError';
import { currencySymbolOf } from '../../../shared/utils/priceCurrency';
import { currentLocaleTag } from '../../../shared/utils/formatters';
import {
  PROCESSING_STEP_DEFS, todayInputValue, isoToDateInput,
} from '../lib/positionFormHelpers';
import {
  MAX_MONEY, MAX_PERCENT, toInputValue,
} from '../../../shared/utils/numberInput';

export const CURRENCIES = ['TRY', 'USD', 'EUR'];
// Mirror DepositValidator.MIN_START_DATE / MAX_TERM_YEARS so out-of-range dates are caught client-side.
export const MIN_DEPOSIT_START = '2000-01-04';
export const MAX_DEPOSIT_TERM_YEARS = 30;
// Prefill matching the backend default (PortfolioProperties.deposit.withholdingTaxRate = 0.15). Türkiye deposit
// stopaj varies by term/decree, so it is only a starting suggestion the holder can change.
const DEFAULT_STOPAJ_PCT = 15;

export function plusYearsInput(dateInput, years) {
  const d = new Date(`${dateInput}T00:00:00`);
  d.setFullYear(d.getFullYear() + years);
  return d.toISOString().slice(0, 10);
}

export function plusMonthsInput(dateInput, months) {
  const d = new Date(`${dateInput}T00:00:00`);
  d.setMonth(d.getMonth() + months);
  return d.toISOString().slice(0, 10);
}

export const CUSTOM_RATE_KEY = '__custom__';

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

export function useDepositForm({ mode = 'add', portfolioId, deposit, onClose, onComplete }) {
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
      toast.success(isEdit ? t('deposits.form.success.titleEdit') : t('deposits.form.success.titleAdd'),
        t('deposits.form.success.subtitle'));
      onComplete?.();
      onClose();
    } catch (err) {
      resetProcessing();
      setError(extractApiError(err, isEdit ? t('deposits.errors.updateFailed') : t('deposits.errors.addFailed')));
      setPhase('form');
    }
  };

  const localeTag = currentLocaleTag();
  const startDisplay = startDate ? new Date(startDate).toLocaleDateString(localeTag, { day: '2-digit', month: 'long', year: 'numeric' }) : '—';
  const maturityDisplay = maturityDate ? new Date(maturityDate).toLocaleDateString(localeTag, { day: '2-digit', month: 'long', year: 'numeric' }) : '—';

  // Re-seed the rate from the chosen term's quote on the NEW start date, and shift the suggested
  // maturity to keep the term length.
  const handleStartDateChange = (iso) => {
    setStartDate(iso);
    setRateTouched(false);
    if (selectedRate?.termMonths) setMaturityDate(plusMonthsInput(iso, selectedRate.termMonths));
    setError(null);
  };

  return {
    t,
    isEdit,
    currency,
    setCurrency,
    principal,
    setPrincipal,
    setAnnualRate,
    indicatorCode,
    setIndicatorCode,
    startDate,
    setStartDate,
    maturityDate,
    setMaturityDate,
    withholdingPct,
    setWithholdingPct,
    rateMenuOpen,
    setRateMenuOpen,
    setRateTouched,
    phase,
    setPhase,
    error,
    setError,
    processingStep,
    processingSteps,
    depositRates,
    ratesLoading,
    selectedRate,
    suggestedRate,
    rateValue,
    breakdown,
    handleRateSelect,
    dismissable,
    sym,
    handleSubmit,
    handleConfirm,
    handleStartDateChange,
    localeTag,
    startDisplay,
    maturityDisplay,
  };
}
