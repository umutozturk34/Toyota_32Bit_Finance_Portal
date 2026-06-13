import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import useProcessingAnimation from '../../../shared/hooks/useProcessingAnimation';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { useAddPosition, usePortfolioLimits, useUpdatePosition } from './usePortfolioData';
import {
  FRACTIONAL_TYPES, ONE_HOUR_MS, SUCCESS_HOLD_MS, PROCESSING_STEP_DEFS,
  todayInputValue, dateInputToIso, isoToDateInput, buildInitialState,
  resolveTarget, toYearMonth, buildPriceIndex, latestPriceAtOrBefore, resolveNativeCurrency,
} from '../lib/positionFormHelpers';
import { MAX_MONEY, MAX_QUANTITY, PRICE_DECIMALS, QUANTITY_DECIMALS, clampNumberInput, sanitizeNumberInput, toInputValue } from '../../../shared/utils/numberInput';

export function usePositionForm({ mode, portfolioId, asset, position, onClose, onComplete }) {
  const { t } = useTranslation();
  const { rateAt, currency: displayCurrency } = useRateHistory();
  const target = resolveTarget(mode, asset, position);
  const isFractional = FRACTIONAL_TYPES.includes(target.assetType);
  const isEdit = mode === 'edit';
  const nativeCurrency = resolveNativeCurrency(target, asset);
  const inputCurrency = displayCurrency === 'ORIGINAL' ? nativeCurrency : displayCurrency;
  const processingSteps = useMemo(
    () => PROCESSING_STEP_DEFS.map((s) => ({ label: t(`positionForm.steps.${s.labelKey}`), duration: s.duration })),
    [t],
  );

  const tryToDisplay = useCallback((tryValue, dateStr) => {
    if (tryValue == null || tryValue === '') return null;
    const num = Number(tryValue);
    if (!Number.isFinite(num)) return null;
    let display = num;
    if (inputCurrency !== 'TRY') {
      const rate = rateAt(inputCurrency, dateStr);
      display = rate != null && rate > 0 ? num / rate : num;
    }
    // Round to the input's allowed precision: a TRY→USD/EUR division otherwise yields a 15-digit float that,
    // when prefilled, would fail the same @Digits(fraction=8) validation a typed value passes — our own
    // suggested price must be valid input. Keeps the hint, prefill and "applied" check on one rounded value.
    return Number(display.toFixed(PRICE_DECIMALS));
  }, [inputCurrency, rateAt]);

  const displayToTry = useCallback((displayValue, dateStr) => {
    if (displayValue == null || displayValue === '') return null;
    const num = Number(displayValue);
    if (!Number.isFinite(num)) return null;
    if (inputCurrency === 'TRY') return num;
    const rate = rateAt(inputCurrency, dateStr);
    return rate != null && rate > 0 ? num * rate : num;
  }, [inputCurrency, rateAt]);

  const [form, setForm] = useState(() => ({ ...buildInitialState(mode, asset, position), entryPrice: '' }));
  const [error, setError] = useState(null);
  const [priceTouched, setPriceTouched] = useState(isEdit);
  const [phase, setPhase] = useState('form');
  const [viewMonth, setViewMonth] = useState(() => toYearMonth(buildInitialState(mode, asset, position).entryDate));
  const [initialFilled, setInitialFilled] = useState(false);
  const [closeEnabled, setCloseEnabled] = useState(false);
  const [exitDate, setExitDate] = useState(todayInputValue());
  const [exitPrice, setExitPrice] = useState('');
  const [exitPriceTouched, setExitPriceTouched] = useState(false);
  const [exitViewMonth, setExitViewMonth] = useState(() => toYearMonth(todayInputValue()));

  const todayIso = todayInputValue();
  const editEntryDateIso = useMemo(
    () => (isEdit && position?.entryDate ? isoToDateInput(position.entryDate) : null),
    [isEdit, position?.entryDate],
  );
  const initialSeedTry = isEdit ? position?.entryPrice : asset?.currentPrice;
  const initialSeedDate = editEntryDateIso ?? todayIso;
  const initialSeedDisplay = useMemo(
    () => tryToDisplay(initialSeedTry, initialSeedDate),
    [initialSeedTry, initialSeedDate, tryToDisplay],
  );
  if (!initialFilled) {
    if (initialSeedTry == null) {
      setInitialFilled(true);
    } else if (initialSeedDisplay != null) {
      setInitialFilled(true);
      setForm((prev) => ({ ...prev, entryPrice: String(initialSeedDisplay) }));
    }
  }

  const entryMonth = toYearMonth(form.entryDate);

  const { processingStep, runAnimation, reset: resetProcessing } = useProcessingAnimation();
  const { data: limits } = usePortfolioLimits();

  const { data: viewAvailability, isFetching: viewLoading, isPending: viewInitialLoading } = useQuery({
    queryKey: ['marketAvailability', target.assetType, target.assetCode, viewMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability(target.assetType, target.assetCode, viewMonth),
    enabled: Boolean(target.assetType && target.assetCode && viewMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });

  const entryLoading = viewLoading;
  const viewPrices = useMemo(() => buildPriceIndex(viewAvailability), [viewAvailability]);
  const highlightedDates = useMemo(() => new Set(viewPrices.keys()), [viewPrices]);
  const suggestedPriceTry = entryMonth === viewMonth ? latestPriceAtOrBefore(viewPrices, form.entryDate) : undefined;
  const suggestedPriceDisplay = useMemo(
    () => tryToDisplay(suggestedPriceTry, form.entryDate),
    [suggestedPriceTry, form.entryDate, tryToDisplay],
  );
  const dataAvailable = suggestedPriceDisplay != null;

  const { data: exitAvailability, isFetching: exitLoading } = useQuery({
    queryKey: ['marketAvailability', target.assetType, target.assetCode, exitViewMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability(target.assetType, target.assetCode, exitViewMonth),
    enabled: Boolean(closeEnabled && target.assetType && target.assetCode && exitViewMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });
  const exitPrices = useMemo(() => buildPriceIndex(exitAvailability), [exitAvailability]);
  const exitHighlights = useMemo(() => new Set(exitPrices.keys()), [exitPrices]);
  const exitSuggestedTry = toYearMonth(exitDate) === exitViewMonth ? latestPriceAtOrBefore(exitPrices, exitDate) : undefined;
  const exitSuggestedDisplay = useMemo(
    () => tryToDisplay(exitSuggestedTry, exitDate),
    [exitSuggestedTry, exitDate, tryToDisplay],
  );
  const [exitSyncKey, setExitSyncKey] = useState(null);
  const eKey = (!closeEnabled || exitPriceTouched) ? null : `${exitDate}|${exitSuggestedDisplay ?? 'none'}`;
  if (eKey !== null && eKey !== exitSyncKey) {
    setExitSyncKey(eKey);
    if (exitSuggestedDisplay != null) setExitPrice(String(exitSuggestedDisplay));
  }

  const addMutation = useAddPosition(portfolioId);
  const updateMutation = useUpdatePosition(portfolioId);

  const [lastSyncedKey, setLastSyncedKey] = useState(null);
  const syncKey = entryLoading || priceTouched
    ? null
    : `${form.entryDate}|${suggestedPriceDisplay ?? 'none'}|${inputCurrency}`;
  if (syncKey !== null && syncKey !== lastSyncedKey) {
    setLastSyncedKey(syncKey);
    if (suggestedPriceDisplay != null) {
      setForm((prev) => ({ ...prev, entryPrice: toInputValue(suggestedPriceDisplay) }));
    } else if (form.entryDate !== todayIso) {
      setForm((prev) => prev.entryPrice ? { ...prev, entryPrice: '' } : prev);
    }
  }

  const totalCostTry = useMemo(() => {
    const q = Number(form.quantity);
    const priceTry = displayToTry(form.entryPrice, form.entryDate);
    return q > 0 && priceTry != null && priceTry > 0 ? q * priceTry : null;
  }, [form.quantity, form.entryPrice, form.entryDate, displayToTry]);

  const handleDateChange = (iso) => {
    setForm((prev) => ({ ...prev, entryDate: iso }));
    setPriceTouched(false);
    setError(null);
  };

  const handlePriceChange = (e) => {
    // Cap magnitude (≤ MAX_MONEY) and decimal places (≤4, the price column scale) as the user types, so a
    // value the backend @DecimalMax/@Digits would reject can't be entered in the first place.
    setForm((prev) => ({ ...prev, entryPrice: sanitizeNumberInput(e.target.value, MAX_MONEY, PRICE_DECIMALS) }));
    setPriceTouched(true);
    setError(null);
  };

  const handleQuantityChange = (e) => {
    const raw = e.target.value;
    // Fractional assets keep ≤8 decimals (quantity column scale); share-based ones stay integer. Both cap at
    // MAX_QUANTITY, mirroring the backend bounds.
    const value = isFractional
      ? sanitizeNumberInput(raw, MAX_QUANTITY, QUANTITY_DECIMALS)
      : clampNumberInput(raw.replace(/[.,]/g, ''), MAX_QUANTITY);
    setForm((prev) => ({ ...prev, quantity: value }));
    setError(null);
  };

  const useSuggestedPrice = () => {
    if (suggestedPriceDisplay == null) return;
    setForm((prev) => ({ ...prev, entryPrice: toInputValue(suggestedPriceDisplay) }));
    setPriceTouched(false);
  };

  const validate = () => {
    if (!form.entryDate) return t('positionForm.errors.dateRequired');
    const price = Number(form.entryPrice);
    if (!form.entryPrice || price <= 0 || price > MAX_MONEY) return t('positionForm.errors.priceInvalid');
    const qty = Number(form.quantity);
    if (!qty || qty <= 0 || qty > MAX_QUANTITY) return t('positionForm.errors.quantityInvalid');
    if (!isFractional && !Number.isInteger(qty)) return t('positionForm.errors.quantityInteger');
    if (closeEnabled) {
      if (!exitDate) return t('positionForm.errors.exitDateRequired', { defaultValue: 'Çıkış tarihi gerekli' });
      if (!exitPrice || Number(exitPrice) <= 0 || Number(exitPrice) > MAX_MONEY) return t('positionForm.errors.exitPriceInvalid', { defaultValue: 'Çıkış fiyatı geçersiz' });
      if (new Date(exitDate) < new Date(form.entryDate)) {
        return t('positionForm.errors.exitBeforeEntry', { defaultValue: 'Çıkış tarihi giriş tarihinden önce olamaz' });
      }
    }
    return null;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setPhase('confirm');
  };

  const handleConfirm = async () => {
    setError(null);
    setPhase('processing');
    const entryPriceNative = Number(form.entryPrice);
    if (!Number.isFinite(entryPriceNative) || entryPriceNative <= 0) {
      setError(t('positionForm.errors.priceInvalid'));
      setPhase('form');
      return;
    }
    const exitPriceNative = closeEnabled && exitPrice ? Number(exitPrice) : null;
    if (closeEnabled && (exitPriceNative == null || !Number.isFinite(exitPriceNative) || exitPriceNative <= 0)) {
      setError(t('positionForm.errors.exitPriceInvalid', { defaultValue: 'Çıkış fiyatı geçersiz' }));
      setPhase('form');
      return;
    }
    const payload = {
      assetType: target.assetType,
      assetCode: target.assetCode,
      quantity: Number(form.quantity),
      entryDate: dateInputToIso(form.entryDate),
      entryPrice: entryPriceNative,
      exitDate: closeEnabled ? dateInputToIso(exitDate) : null,
      exitPrice: closeEnabled ? exitPriceNative : null,
      priceCurrency: inputCurrency,
    };
    const mutate = isEdit
      ? () => updateMutation.mutateAsync({ positionId: position.id, payload })
      : () => addMutation.mutateAsync(payload);
    try {
      await Promise.all([mutate(), runAnimation(processingSteps)]);
      setPhase('success');
      setTimeout(() => { onComplete?.(); onClose(); }, SUCCESS_HOLD_MS);
    } catch (err) {
      resetProcessing();
      setError(err?.response?.data?.message || (isEdit ? t('positionForm.errors.updateFailed') : t('positionForm.errors.addFailed')));
      setPhase('form');
    }
  };

  return {
    target,
    isFractional,
    isEdit,
    inputCurrency,
    processingSteps,
    processingStep,
    form,
    error,
    phase,
    priceTouched,
    limits,
    highlightedDates,
    viewInitialLoading,
    entryLoading,
    dataAvailable,
    suggestedPriceDisplay,
    closeEnabled,
    exitDate,
    exitPrice,
    exitHighlights,
    exitLoading,
    totalCostTry,
    setViewMonth,
    setCloseEnabled,
    setExitDate,
    setExitPrice,
    setExitPriceTouched,
    setExitViewMonth,
    setError,
    setPhase,
    handleDateChange,
    handlePriceChange,
    handleQuantityChange,
    useSuggestedPrice,
    handleSubmit,
    handleConfirm,
  };
}
