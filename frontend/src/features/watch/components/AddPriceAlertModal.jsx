import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  AlertCircle, ArrowUp, ArrowDown, TrendingUp, TrendingDown,
  Hash, Target, Anchor, Search, Activity,
} from 'lucide-react';
import BaseModal from '../../../shared/components/modal/BaseModal';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
import { useCreatePriceAlert } from '../../../shared/hooks/usePriceAlerts';
import { toast } from '../../../shared/components/feedback/toastBus';
import { extractApiError } from '../../../shared/utils/apiError';
import { currentLocaleTag } from '../../../shared/utils/formatters';
import { priceCurrencyOf, currencySymbolOf } from '../../../shared/utils/priceCurrency';

const DIRECTION_DEFS = [
  { value: 'ABOVE', Icon: ArrowUp, tone: 'success' },
  { value: 'BELOW', Icon: ArrowDown, tone: 'danger' },
  { value: 'CHANGE_PCT_UP', Icon: TrendingUp, tone: 'success' },
  { value: 'CHANGE_PCT_DOWN', Icon: TrendingDown, tone: 'danger' },
];

const TONE_CLASSES = {
  success: {
    inactive: 'border-border-default text-fg-muted hover:border-success/40 hover:text-success hover:bg-success/5',
    active: 'border-success/60 bg-success/10 text-success shadow-[0_0_18px_-6px_var(--color-success,#10b981)]/20',
  },
  danger: {
    inactive: 'border-border-default text-fg-muted hover:border-danger/40 hover:text-danger hover:bg-danger/5',
    active: 'border-danger/60 bg-danger/10 text-danger shadow-[0_0_18px_-6px_var(--color-danger,#ef4444)]/20',
  },
};

const QUICK_DELTAS = [
  { label: '−10%', value: -0.10 },
  { label: '−5%', value: -0.05 },
  { label: '+5%', value: 0.05 },
  { label: '+10%', value: 0.10 },
];

const PCT_PRESETS = [1, 2, 5, 10];

function formatLocale(value) {
  return Number(value).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 2 });
}

export default function AddPriceAlertModal({
  isOpen,
  onClose,
  defaultMarketType,
  defaultAssetCode,
  defaultReferencePrice,
  defaultCurrency,
}) {
  const { t } = useTranslation();
  const create = useCreatePriceAlert();

  const buildInitialAsset = () =>
    defaultMarketType && defaultAssetCode
      ? {
          type: defaultMarketType,
          code: defaultAssetCode,
          name: defaultAssetCode,
          price: defaultReferencePrice,
          currency: defaultCurrency,
        }
      : null;
  const initialRefStr = defaultReferencePrice != null ? String(defaultReferencePrice) : '';

  const [wasOpen, setWasOpen] = useState(isOpen);
  const [selectedAsset, setSelectedAsset] = useState(buildInitialAsset);
  const [direction, setDirection] = useState('ABOVE');
  const [threshold, setThreshold] = useState(initialRefStr);
  const [referencePrice, setReferencePrice] = useState(initialRefStr);

  if (isOpen && !wasOpen) {
    setWasOpen(true);
    setDirection('ABOVE');
    setReferencePrice(initialRefStr);
    setThreshold(initialRefStr);
    setSelectedAsset(buildInitialAsset());
  } else if (!isOpen && wasOpen) {
    setWasOpen(false);
  }

  const isPercent = direction === 'CHANGE_PCT_UP' || direction === 'CHANGE_PCT_DOWN';
  const requiresReference = isPercent;
  const currentPrice = selectedAsset?.price ?? null;
  const alertCurrency = priceCurrencyOf(selectedAsset);
  const symbol = currencySymbolOf(alertCurrency);

  const activeDir = useMemo(() => DIRECTION_DEFS.find((d) => d.value === direction), [direction]);

  const numericThreshold = Number.parseFloat(threshold);
  const projectedPrice = useMemo(() => {
    if (!isPercent || !Number.isFinite(numericThreshold) || numericThreshold <= 0) return null;
    const ref = Number.parseFloat(referencePrice);
    if (!Number.isFinite(ref) || ref <= 0) return null;
    const sign = direction === 'CHANGE_PCT_UP' ? 1 : -1;
    return ref * (1 + sign * (numericThreshold / 100));
  }, [isPercent, numericThreshold, referencePrice, direction]);

  const distanceFromCurrent = useMemo(() => {
    if (isPercent || !Number.isFinite(numericThreshold) || numericThreshold <= 0 || currentPrice == null) return null;
    const diff = numericThreshold - Number(currentPrice);
    const pct = (diff / Number(currentPrice)) * 100;
    return { diff, pct };
  }, [numericThreshold, currentPrice, isPercent]);

  const applyQuickDelta = (deltaPct) => {
    if (currentPrice == null) return;
    const newThreshold = Number(currentPrice) * (1 + deltaPct);
    setThreshold(newThreshold.toFixed(currentPrice < 1 ? 6 : 2));
    setDirection(deltaPct >= 0 ? 'ABOVE' : 'BELOW');
  };

  const switchDirection = (next) => {
    const wasPercent = isPercent;
    const willBePercent = next === 'CHANGE_PCT_UP' || next === 'CHANGE_PCT_DOWN';
    setDirection(next);
    if (wasPercent !== willBePercent) {
      if (willBePercent) {
        setThreshold('5');
      } else if (currentPrice != null) {
        setThreshold(String(currentPrice));
      }
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!selectedAsset) return toast.error(t('addPriceAlert.errorAsset'));
    if (!Number.isFinite(numericThreshold) || numericThreshold <= 0) {
      return toast.error(t('addPriceAlert.errorThreshold'));
    }
    let numericReference = null;
    if (requiresReference) {
      numericReference = Number.parseFloat(referencePrice);
      if (!Number.isFinite(numericReference) || numericReference <= 0) {
        return toast.error(t('addPriceAlert.errorReference'));
      }
    }
    try {
      await create.mutateAsync({
        marketType: selectedAsset.type,
        assetCode: selectedAsset.code,
        direction,
        threshold: numericThreshold,
        currency: alertCurrency,
        referencePrice: numericReference,
      });
      toast.success(t('addPriceAlert.created', { code: selectedAsset.code }));
      onClose();
    } catch (err) {
      toast.error(extractApiError(err, t('addPriceAlert.failed')));
    }
  };

  const subtitle = selectedAsset
    ? `${selectedAsset.code} (${t(`assets.labels.${selectedAsset.type}`, { defaultValue: selectedAsset.type })})`
    : t('addPriceAlert.subtitlePrompt');

  return (
    <BaseModal
      isOpen={isOpen}
      onClose={onClose}
      icon={AlertCircle}
      title={t('addPriceAlert.title')}
      subtitle={subtitle}
      size="md"
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        {!defaultAssetCode && (
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Search className="h-3 w-3" />
              {t('addPriceAlert.searchLabel')}
            </label>
            {selectedAsset ? (
              <div className="flex items-center justify-between gap-2 rounded-lg border border-accent/40 bg-accent/5 px-3 py-2.5">
                <div className="flex items-center gap-2 min-w-0">
                  {selectedAsset.image && (/^https?:\/\//i.test(selectedAsset.image)
                    ? <img src={selectedAsset.image} alt={selectedAsset.code} className="w-6 h-6 rounded shrink-0" />
                    : <span className="w-6 h-6 rounded shrink-0 flex items-center justify-center text-base leading-none">{selectedAsset.image}</span>)}
                  <span className="text-sm font-mono font-semibold text-fg truncate">{selectedAsset.code}</span>
                  {selectedAsset.name && (
                    <span className="text-xs text-fg-muted truncate">· {selectedAsset.name}</span>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedAsset(null)}
                  className="text-[11px] font-medium text-fg-muted hover:text-fg transition-colors bg-transparent border-none cursor-pointer"
                >
                  {t('addPriceAlert.change')}
                </button>
              </div>
            ) : (
              <SearchSuggestions
                placeholder={t('addPriceAlert.searchPlaceholder')}
                navigateOnSelect={false}
                onSelect={(asset) => {
                  setSelectedAsset(asset);
                  if (asset.price != null) {
                    setReferencePrice(String(asset.price));
                    setThreshold(String(asset.price));
                  }
                }}
              />
            )}
          </div>
        )}

        {currentPrice != null && (
          <div className="rounded-xl border border-accent/20 bg-gradient-to-br from-accent/8 to-accent-secondary/4 px-3.5 py-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent/15">
                <Activity className="h-3.5 w-3.5 text-accent" />
              </div>
              <div className="leading-tight">
                <div className="text-[10px] uppercase tracking-wider text-fg-muted font-semibold">{t('addPriceAlert.currentPrice')}</div>
                <div className="text-[10px] text-fg-subtle">{t('addPriceAlert.liveData')}</div>
              </div>
            </div>
            <div className="flex items-center gap-1.5">
              <div className="text-lg font-mono font-bold text-fg tabular-nums">
                {symbol}{formatLocale(currentPrice)}
              </div>
              <span className="text-[10px] font-mono font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded-md bg-accent/15 text-accent border border-accent/25">
                {alertCurrency}
              </span>
            </div>
          </div>
        )}

        <div className="space-y-2">
          <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <Target className="h-3 w-3" />
            {t('addPriceAlert.triggerLabel')}
          </label>
          <div className="grid grid-cols-2 gap-2">
            {DIRECTION_DEFS.map(({ value, Icon, tone }) => {
              const active = direction === value;
              const cls = TONE_CLASSES[tone];
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => switchDirection(value)}
                  className={`flex items-center gap-2 rounded-lg border px-3 py-2.5 text-xs font-semibold transition-all bg-transparent cursor-pointer ${
                    active ? cls.active : cls.inactive
                  }`}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span className="text-left leading-tight">{t(`addPriceAlert.direction.${value}.label`)}</span>
                </button>
              );
            })}
          </div>
          <p className="text-[11px] text-fg-subtle leading-relaxed pl-0.5">
            {t(`addPriceAlert.direction.${activeDir?.value}.hint`)}
          </p>
        </div>

        {!isPercent && currentPrice != null && (
          <div className="space-y-1.5">
            <label className="text-[11px] font-medium text-fg-muted flex items-center gap-1.5">
              <Hash className="h-3 w-3" />
              {t('addPriceAlert.quickSelect')}
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
              {QUICK_DELTAS.map(({ label, value }) => (
                <button
                  key={label}
                  type="button"
                  onClick={() => applyQuickDelta(value)}
                  className={`rounded-md border border-border-default bg-bg-base px-2 py-1.5 text-[11px] font-mono font-semibold transition-colors cursor-pointer hover:border-accent/40 hover:bg-accent/5 hover:text-accent ${
                    value > 0 ? 'text-success/80 hover:text-success' : 'text-danger/80 hover:text-danger'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        )}

        {isPercent && (
          <div className="space-y-1.5">
            <label className="text-[11px] font-medium text-fg-muted flex items-center gap-1.5">
              <Hash className="h-3 w-3" />
              {t('addPriceAlert.quickPercent')}
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
              {PCT_PRESETS.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setThreshold(String(p))}
                  className="rounded-md border border-border-default bg-bg-base px-2 py-1.5 text-[11px] font-mono font-semibold text-fg-muted transition-colors cursor-pointer hover:border-accent/40 hover:bg-accent/5 hover:text-accent"
                >
                  %{p}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className={`grid ${requiresReference ? 'grid-cols-2' : 'grid-cols-1'} gap-3`}>
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Hash className="h-3 w-3" />
              {isPercent ? t('addPriceAlert.thresholdPctLabel') : t('addPriceAlert.thresholdPriceLabel')}
            </label>
            <div className="relative">
              <input
                type="number"
                step={isPercent ? '0.1' : '0.0001'}
                min="0"
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
                placeholder={isPercent ? '5' : (currentPrice != null ? formatLocale(currentPrice) : '100000')}
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 pr-10 text-sm text-fg font-mono outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[11px] font-mono text-fg-subtle pointer-events-none">
                {isPercent ? '%' : `${symbol} ${alertCurrency}`}
              </span>
            </div>
            {distanceFromCurrent != null && (
              <p className={`text-[11px] font-mono leading-relaxed ${distanceFromCurrent.pct >= 0 ? 'text-success/80' : 'text-danger/80'}`}>
                {t('addPriceAlert.distanceFromCurrent', {
                  signed: `${distanceFromCurrent.pct >= 0 ? '+' : ''}${distanceFromCurrent.pct.toFixed(2)}`,
                })}
              </p>
            )}
            {projectedPrice != null && (
              <p className="text-[11px] font-mono text-fg-subtle leading-relaxed">
                {t('addPriceAlert.projected', { price: formatLocale(projectedPrice) })}
              </p>
            )}
          </div>
          {requiresReference && (
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Anchor className="h-3 w-3" />
                {t('addPriceAlert.referenceLabel')}
              </label>
              <input
                type="number"
                step="0.0001"
                min="0"
                value={referencePrice}
                onChange={(e) => setReferencePrice(e.target.value)}
                placeholder={t('addPriceAlert.referencePlaceholder')}
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>
          )}
        </div>

        <button
          type="submit"
          disabled={create.isPending || !selectedAsset}
          className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright shadow-lg shadow-accent/25 transition-all border-none cursor-pointer disabled:opacity-50"
        >
          {create.isPending ? t('addPriceAlert.creating') : t('addPriceAlert.createCta')}
        </button>
      </form>
    </BaseModal>
  );
}
