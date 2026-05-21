import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { ONE_HOUR_MS, toYearMonth, buildPriceIndex } from '../lib/positionFormHelpers';

export const todayIso = () => new Date().toISOString().slice(0, 10);

export const yesterdayIso = () => {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return d.toISOString().slice(0, 10);
};

export function formatDateLabel(iso, localeTag) {
  if (!iso) return '';
  return new Date(`${iso}T00:00:00`).toLocaleDateString(localeTag, {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

export function usePositionCloseForm({
  availabilityAssetType,
  availabilityAssetCode,
  entryDateIso,
  initialDate,
  initialPrice,
  liveSuggestedPriceTry,
}) {
  const { t } = useTranslation();
  const { format: money, currency: displayCurrency } = useMoney();
  const { convertAt, rateAt } = useRateHistory();
  const localeTag = t('common.localeTag');
  const inputCurrency = displayCurrency === 'ORIGINAL' || !displayCurrency ? 'TRY' : displayCurrency;

  const [date, setDate] = useState(() => initialDate ?? todayIso());
  const [price, setPrice] = useState(() => initialPrice ?? '');
  const [priceTouched, setPriceTouched] = useState(false);
  const [error, setError] = useState(null);
  const [viewMonth, setViewMonth] = useState(() => toYearMonth(todayIso()));

  const { data: viewAvailability, isFetching: viewLoading } = useQuery({
    queryKey: ['marketAvailability', availabilityAssetType, availabilityAssetCode, viewMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability(availabilityAssetType, availabilityAssetCode, viewMonth),
    enabled: Boolean(availabilityAssetType && availabilityAssetCode && viewMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });

  const viewPrices = useMemo(() => buildPriceIndex(viewAvailability), [viewAvailability]);
  const highlightedDates = useMemo(() => new Set(viewPrices.keys()), [viewPrices]);
  const dateMonth = useMemo(() => toYearMonth(date), [date]);
  const historicalPriceTry = dateMonth === viewMonth ? viewPrices.get(date) : undefined;
  const historicalPriceDisplay = historicalPriceTry != null
    ? Number(convertAt(historicalPriceTry, 'TRY', date) ?? historicalPriceTry)
    : null;

  const today = todayIso();
  const isToday = date === today;

  const liveSuggestedInDisplay = liveSuggestedPriceTry != null
    ? Number(convertAt(liveSuggestedPriceTry, 'TRY', today) ?? liveSuggestedPriceTry)
    : null;

  const dateSuggestedInDisplay = useMemo(() => {
    if (historicalPriceDisplay != null) return historicalPriceDisplay;
    if (isToday && liveSuggestedInDisplay != null) return liveSuggestedInDisplay;
    return null;
  }, [historicalPriceDisplay, isToday, liveSuggestedInDisplay]);

  const applyDatePrice = () => {
    if (dateSuggestedInDisplay != null) setPrice(String(dateSuggestedInDisplay));
  };

  const syncKey = `${date}|${dateSuggestedInDisplay ?? 'none'}`;
  const [trackedKey, setTrackedKey] = useState(syncKey);
  if (syncKey !== trackedKey) {
    setTrackedKey(syncKey);
    if (!priceTouched && dateSuggestedInDisplay != null) {
      setPrice(String(dateSuggestedInDisplay));
    }
  }

  const parsedPrice = Number(price);
  const validPrice = Number.isFinite(parsedPrice) && parsedPrice > 0;
  const validDate = date && (!entryDateIso || date >= entryDateIso) && date <= today;

  const toTryOnDate = (value, dateStr) => {
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    if (inputCurrency === 'TRY') return num;
    const rate = rateAt(inputCurrency, dateStr);
    return rate != null && rate > 0 ? num * rate : num;
  };

  const handleMonthChange = (y, m) => setViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`);

  const datePresets = [
    { id: 'today', iso: today, label: t('portfolio.sell.today', { defaultValue: 'Bugün' }) },
    { id: 'yesterday', iso: yesterdayIso(), label: t('portfolio.sell.yesterday', { defaultValue: 'Dün' }) },
  ];

  return {
    t,
    money,
    displayCurrency,
    inputCurrency,
    localeTag,
    convertAt,
    today,
    isToday,
    date,
    setDate,
    price,
    setPrice,
    parsedPrice,
    validPrice,
    validDate,
    priceTouched,
    setPriceTouched,
    error,
    setError,
    highlightedDates,
    viewLoading,
    dateSuggestedInDisplay,
    applyDatePrice,
    handleMonthChange,
    datePresets,
    toTryOnDate,
  };
}
