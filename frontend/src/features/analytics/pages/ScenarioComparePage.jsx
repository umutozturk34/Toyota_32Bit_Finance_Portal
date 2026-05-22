import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Play, AlertCircle, Sparkles } from 'lucide-react';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import LoadingState from '../../../shared/components/feedback/LoadingState';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import InstrumentPicker from '../components/InstrumentPicker';
import CompareChart from '../components/CompareChart';
import ScenarioRankingTable from '../components/ScenarioRankingTable';
import { useScenarioSimulation } from '../hooks/useAnalytics';
import { dateOffsetIso, formatPercent, todayIso } from '../utils';
import { useMoney } from '../../../shared/hooks/useMoney';

const QUICK_AMOUNTS = [10000, 50000, 100000, 500000];

export default function ScenarioComparePage() {
  const { t } = useTranslation();
  const { currency: displayCurrency, rates } = useMoney();
  const [amount, setAmount] = useState(100000);
  const [startDate, setStartDate] = useState(dateOffsetIso(12));
  const [endDate, setEndDate] = useState(todayIso());
  const [instruments, setInstruments] = useState([
    { type: 'DEPOSIT', code: 'TP.TRYTAS.MT06', name: 'TRY 3M Mevduat' },
    { type: 'FOREX', code: 'USD', name: 'USD/TRY' },
    { type: 'COMMODITY', code: 'XAUTRY', name: 'Altın' },
  ]);

  const inputCurrency = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;
  const simulation = useScenarioSimulation();

  function run() {
    if (instruments.length === 0 || !amount || !startDate) return;
    const numericAmount = Number(amount);
    const fxRate = rates?.[inputCurrency];
    const amountTry = inputCurrency === 'TRY' || !fxRate ? numericAmount : numericAmount * fxRate;
    simulation.mutate({
      amount: amountTry,
      startDate,
      endDate: endDate || null,
      instruments: instruments.map(({ type, code }) => ({ type, code })),
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
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg tracking-tight leading-none">
          {t('analytics.scenarioTitle', { defaultValue: 'Senaryo Karşılaştırması' })}
        </h1>
        <p className="mt-2 text-sm text-fg-muted max-w-2xl">
          {t('analytics.scenarioSubtitle', { defaultValue: 'Belirli bir tarihte belirli bir miktarı farklı enstrümanlara koysaydın bugün ne kadar olurdu? Gerçek geçmiş veriyle simüle et.' })}
        </p>
      </header>

      <Card variant="elevated" radius="xl" padding="lg" backdropBlur className="space-y-5">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Field label={`${t('analytics.amount', { defaultValue: 'Tutar' })} (${inputCurrency})`}>
            <div className="flex flex-col gap-1.5">
              <input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="text-lg font-mono font-bold tabular-nums bg-bg-base border border-border-default/60 rounded-lg px-3 py-2 text-fg focus:border-accent outline-none"
              />
              <div className="flex gap-1.5">
                {QUICK_AMOUNTS.map((q) => (
                  <button
                    key={q}
                    type="button"
                    onClick={() => setAmount(q)}
                    className={`text-[10px] font-mono font-semibold rounded-md px-2 py-0.5 cursor-pointer border-none transition-colors ${
                      Number(amount) === q ? 'bg-accent/20 text-accent' : 'bg-bg-elevated text-fg-muted hover:text-fg'
                    }`}
                  >
                    {q.toLocaleString('tr-TR')}
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
          {simulation.isError && (
            <div className="flex items-center gap-2 text-xs text-red-500">
              <AlertCircle className="h-4 w-4" />
              {simulation.error?.response?.data?.message || t('analytics.simulationError', { defaultValue: 'Simülasyon başarısız' })}
            </div>
          )}
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

      {!scenario && !simulation.isPending && simulation.isError && (
        <ErrorState
          fullscreen={false}
          message={simulation.error?.response?.data?.message || t('analytics.simulationError', { defaultValue: 'Simülasyon başarısız' })}
          onRetry={run}
          retryLoading={simulation.isPending}
        />
      )}

      {!scenario && !simulation.isPending && !simulation.isError && (
        <EmptyState
          size="md"
          icon={<Sparkles className="h-5 w-5 text-accent" />}
          title={t('analytics.scenarioEmptyTitle', { defaultValue: 'Henüz simülasyon yok' })}
          message={t('analytics.scenarioEmptyMessage', { defaultValue: 'Tutar, tarih ve enstrümanları seç, ardından "Simüle Et"e bas.' })}
        />
      )}

      {scenario && (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
          {cpiPct != null && (
            <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 flex items-center gap-3">
              <span className="h-2 w-2 rounded-full bg-amber-500 animate-pulse" />
              <span className="text-xs font-mono uppercase tracking-[0.16em] text-fg-muted">
                {t('analytics.cpiGrowth', { defaultValue: 'TÜFE büyümesi (dönemde)' })}
              </span>
              <span className="font-mono font-bold tabular-nums text-amber-500 text-sm">
                {formatPercent(cpiPct)}
              </span>
              <span className="ml-auto text-[10px] font-mono text-fg-subtle uppercase tracking-[0.14em]">
                {t('analytics.realReturnHint', { defaultValue: 'Reel getiri = nominal − TÜFE etkisi' })}
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
      <label className="text-[10px] font-mono uppercase tracking-[0.18em] text-fg-muted">{label}</label>
      {children}
    </div>
  );
}
