import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Play, Zap } from 'lucide-react';
import { isValidationError } from '../../../shared/utils/apiError';
import { clampNumberInput, MAX_MONEY } from '../../../shared/utils/numberInput';
import useSessionState from '../../../shared/hooks/useSessionState';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import LoadingState from '../../../shared/components/feedback/LoadingState';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import InstrumentPicker from '../components/InstrumentPicker';
import CompareChart from '../components/CompareChart';
import ScenarioRankingTable from '../components/ScenarioRankingTable';
import { useScenarioSimulation } from '../hooks/useAnalytics';
import { dateOffsetIso, formatPercent, todayIso } from '../utils';
import { useMoney } from '../../../shared/hooks/useMoney';
import { depositCurrencyFor } from '../lib/compareSeriesUtils';

const QUICK_AMOUNTS = [10000, 50000, 100000, 500000];

const CURRENCY_SYMBOL = { TRY: '₺', USD: '$', EUR: '€' };

function trimDecimal(n) {
  return n % 1 === 0 ? String(n) : n.toFixed(1).replace(/\.0$/, '');
}

function humanizeAmount(value, locale, t) {
  const num = Number(value);
  if (!Number.isFinite(num) || num === 0) return '';
  const abs = Math.abs(num);
  if (abs >= 1e9) return `${trimDecimal(num / 1e9)}${t('numberFormat.billion')}`;
  if (abs >= 1e6) return `${trimDecimal(num / 1e6)}${t('numberFormat.million')}`;
  if (abs >= 1e3) return `${trimDecimal(num / 1e3)}${t('numberFormat.thousand')}`;
  return num.toLocaleString(locale === 'tr' ? 'tr-TR' : 'en-US');
}

export default function ScenarioComparePage() {
  const { t, i18n } = useTranslation();
  const { currency: displayCurrency } = useMoney();
  const [amount, setAmount] = useSessionState('scenario:amount', 100000);
  const [startDate, setStartDate] = useSessionState('scenario:startDate', dateOffsetIso(12));
  const [endDate, setEndDate] = useSessionState('scenario:endDate', todayIso());
  const [instruments, setInstruments] = useSessionState('scenario:instruments', [
    { type: 'DEPOSIT', code: 'TP.TRYTAS.MT02', name: 'TRY 3M Mevduat', labelKey: 'analytics.preset.depositTry3m' },
    { type: 'FOREX', code: 'USD', name: 'USD/TRY', labelKey: 'analytics.preset.usdTry' },
    { type: 'COMMODITY', code: 'XAUTRY', name: 'Altın', labelKey: 'analytics.preset.gold' },
  ]);

  // A single non-TRY deposit's own currency is the DEFAULT frame (a USD/EUR deposit makes that currency the
  // base) — but only under the "Original" display setting; an explicit currency choice overrides it below.
  const depositFrameCurrency = (() => {
    const set = new Set(
      instruments
        .filter((i) => i.type === 'DEPOSIT')
        .map((i) => depositCurrencyFor(i.code))
        .filter((c) => c !== 'TRY'),
    );
    return set.size === 1 ? [...set][0] : null;
  })();
  // An explicit display-currency choice (TRY/USD/EUR) wins: a foreign deposit converts per-date, so a USD
  // deposit with TRY selected computes over the entered TRY. "Original" falls back to the deposit's own
  // currency (then TRY when there is no single non-TRY deposit).
  const inputCurrency = displayCurrency !== 'ORIGINAL'
    ? displayCurrency
    : (depositFrameCurrency || 'TRY');
  const simulation = useScenarioSimulation();

  function run() {
    if (instruments.length === 0 || !amount || !startDate) return;
    const numericAmount = Number(amount);
    simulation.mutate({
      amount: numericAmount,
      startDate,
      endDate: endDate || null,
      instruments: instruments.map(({ type, code }) => ({ type, code })),
      targetCurrency: inputCurrency,
    });
  }

  const scenario = simulation.data;
  const cpiPct = scenario?.cpiGrowthPct;

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="space-y-6"
    >
      <header className="pb-3 border-b border-border-default/40">
        <div className="flex items-center gap-2.5">
          <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/12 text-accent shrink-0">
            <Zap className="h-5 w-5" />
          </span>
          <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg tracking-tight leading-none">
            {t('analytics.scenarioTitle', { defaultValue: 'Senaryo Karşılaştırması' })}
          </h1>
        </div>
        <p className="mt-2 text-sm text-fg-muted max-w-2xl">
          {t('analytics.scenarioSubtitle', { defaultValue: 'Belirli bir tarihte belirli bir miktarı farklı enstrümanlara koysaydın bugün ne kadar olurdu? Gerçek geçmiş veriyle simüle et.' })}
        </p>
      </header>

      {/* relative z-10 lifts the whole controls Card (and its absolute search dropdown, trapped in the Card's
          backdrop-filter stacking context) above the later results/chart sibling — otherwise the chart and its
          ECharts tooltip paint over the open search results. */}
      <Card variant="elevated" radius="xl" padding="lg" backdropBlur clip={false} className="relative z-10 space-y-5">
        {/* z-[60] keeps the date-picker calendars above the InstrumentPicker (its search dropdown sits at
            z-50); without a higher stacking context the calendar opened underneath the picker below it. */}
        <div className="relative z-[60] grid grid-cols-1 md:grid-cols-3 gap-3 sm:gap-4">
          <Field label={t('analytics.amount', { defaultValue: 'Tutar' })}>
            <div className="flex flex-col gap-1.5">
              <div className="relative flex items-center">
                <span className="absolute left-3 text-fg-muted font-mono font-bold text-lg pointer-events-none select-none">
                  {CURRENCY_SYMBOL[inputCurrency] || inputCurrency}
                </span>
                <input
                  type="number"
                  min="0"
                  max="1000000000000"
                  step="any"
                  value={amount}
                  onChange={(e) => setAmount(clampNumberInput(e.target.value, MAX_MONEY))}
                  className="w-full text-lg font-mono font-bold tabular-nums bg-bg-base border border-border-default/60 rounded-lg pl-9 pr-20 py-2 text-fg focus:border-accent outline-none [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                />
                {humanizeAmount(amount, i18n.language?.startsWith("tr") ? "tr" : "en", t) && (
                  <span className="absolute right-3 text-[11px] font-mono font-semibold text-fg-muted pointer-events-none select-none uppercase tracking-wider">
                    {humanizeAmount(amount, i18n.language?.startsWith("tr") ? "tr" : "en", t)}
                  </span>
                )}
              </div>
              <div className="flex gap-1.5 flex-wrap">
                {QUICK_AMOUNTS.map((q) => (
                  <button
                    key={q}
                    type="button"
                    onClick={() => setAmount(q)}
                    className={`text-[10px] font-mono font-semibold rounded-md px-2 py-0.5 cursor-pointer border-none transition-colors ${
                      Number(amount) === q ? 'bg-accent/20 text-accent' : 'bg-bg-elevated text-fg-muted hover:text-fg'
                    }`}
                  >
                    {humanizeAmount(q, i18n.language?.startsWith("tr") ? "tr" : "en", t)}
                  </button>
                ))}
              </div>
              <div className="text-[10px] font-mono text-fg-subtle italic">
                {t('analytics.amountHint', { defaultValue: 'Ayarlardan görüntü para birimini değiştirebilirsin' })}
              </div>
            </div>
          </Field>

          <Field label={t('analytics.startDate', { defaultValue: 'Başlangıç' })}>
            <DatePickerPopover
              value={startDate}
              onChange={setStartDate}
              maxDate={endDate || todayIso()}
            />
          </Field>

          <Field label={t('analytics.endDate', { defaultValue: 'Bitiş' })}>
            <DatePickerPopover
              value={endDate}
              onChange={setEndDate}
              minDate={startDate}
              maxDate={todayIso()}
            />
          </Field>
        </div>

        <InstrumentPicker value={instruments} onChange={setInstruments} max={6} />

        <div className="flex items-center justify-between flex-wrap gap-3 pt-3 border-t border-border-default/40">
          <motion.button
            type="button"
            onClick={run}
            disabled={instruments.length === 0 || simulation.isPending}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className="ml-auto inline-flex items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-semibold bg-accent text-accent-fg disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer border-none transition-shadow shadow-lg shadow-accent/20 hover:shadow-accent/40"
          >
            {simulation.isPending ? <Spinner size="xs" /> : <Play className="h-4 w-4" />}
            {t('analytics.simulate', { defaultValue: 'Simüle Et' })}
          </motion.button>
        </div>
      </Card>

      {!scenario && simulation.isPending && (
        <LoadingState
          fullscreen={false}
          message={t('analytics.simulationRunning', { defaultValue: 'Simülasyon hesaplanıyor...' })}
        />
      )}

      {!scenario && !simulation.isPending && simulation.isError && !isValidationError(simulation.error) && (
        <ErrorState
          fullscreen={false}
          message={simulation.error?.response?.data?.message || t('analytics.simulationError', { defaultValue: 'Simülasyon başarısız' })}
          onRetry={run}
          retryLoading={simulation.isPending}
        />
      )}

      {scenario && (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
          {cpiPct != null && (
            <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-3 sm:px-4 py-3 flex items-center gap-2 sm:gap-3 flex-wrap">
              <span className="h-2 w-2 rounded-full bg-amber-500 animate-pulse" />
              <span className="text-sm font-display font-semibold text-fg-muted">
                {t('analytics.cpiGrowth', { defaultValue: 'TÜFE büyümesi (dönemde)' })}
              </span>
              <span className="font-mono font-bold tabular-nums text-amber-500 text-sm">
                {formatPercent(cpiPct)}
              </span>
              <span className="sm:ml-auto text-xs font-mono text-fg-subtle">
                {t('analytics.realReturnHint', { defaultValue: 'Reel getiri ≈ (1+nominal)/(1+TÜFE) − 1' })}
              </span>
            </div>
          )}
          <CompareChart scenario={scenario} />
          <ScenarioRankingTable scenario={scenario} />
        </motion.div>
      )}
    </motion.div>
  );
}

function Field({ label, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-display font-semibold text-fg-muted">{label}</label>
      {children}
    </div>
  );
}
