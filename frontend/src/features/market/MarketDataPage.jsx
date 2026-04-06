import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  DollarSign, ArrowDownUp, Gem, CircleDot, Star,
  TrendingUp, TrendingDown, Coins, BarChart3,
} from 'lucide-react';
import { exchangeRateService, metalService } from '../../shared/services/dataService';
import PageHeader from '../../shared/components/PageHeader';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import EmptyState from '../../shared/components/EmptyState';
import { containerVariants, cardVariants } from '../../shared/utils/animations';

const CURRENCY_ICONS = {
  USD: DollarSign, EUR: Coins, GBP: DollarSign, JPY: DollarSign,
  CHF: DollarSign, CAD: DollarSign, AUD: DollarSign, SAR: DollarSign,
  KWD: DollarSign, SEK: DollarSign, NOK: DollarSign, DKK: DollarSign,
};

const METAL_NAMES = {
  PAXG: 'PAX Gold (Altın)', XAUT: 'Tether Gold (Altın)', KAG: 'Kinesis Silver (Gümüş)',
  GOLD: 'Altın', SILVER: 'Gümüş', PLATINUM: 'Platin',
};

const METAL_ICON_STYLES = {
  PAXG: 'text-warning', XAUT: 'text-warning', KAG: 'text-fg-muted',
  GOLD: 'text-warning', SILVER: 'text-fg-muted', PLATINUM: 'text-accent-bright',
};

const tabs = [
  { key: 'forex', label: 'Döviz Kurları', Icon: ArrowDownUp },
  { key: 'metals', label: 'Kıymetli Madenler', Icon: Gem },
];

function formatRate(rate) {
  return parseFloat(rate).toFixed(4);
}

function formatDate(dateString) {
  return new Date(dateString).toLocaleDateString('tr-TR', {
    year: 'numeric', month: 'long', day: 'numeric',
  });
}

export default function MarketDataPage() {
  const [rates, setRates] = useState([]);
  const [metals, setMetals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('forex');

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [ratesRes, metalsRes] = await Promise.all([
        exchangeRateService.getRates(),
        metalService.getLatestPrices().catch(() => ({ success: false, data: [] })),
      ]);
      if (ratesRes.success && ratesRes.data) setRates(ratesRes.data);
      if (metalsRes.success && metalsRes.data) setMetals(metalsRes.data);
    } catch {
      setError('Piyasa verileri yüklenemedi. Lütfen tekrar deneyin.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  if (loading) return <LoadingState message="Piyasa verileri yükleniyor..." />;
  if (error) return <ErrorState message={error} onRetry={fetchData} />;

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BarChart3 className="h-5 w-5" />}
        title="Piyasa Verileri"
        subtitle="Döviz kurları ve kıymetli maden fiyatları"
        onRefresh={fetchData}
        loading={loading}
      />

      <div className="flex rounded-xl border border-border-default bg-bg-elevated p-1 w-fit">
        {tabs.map(({ key, label, Icon }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-all border-none cursor-pointer ${
              activeTab === key
                ? 'bg-accent/15 text-accent-bright'
                : 'text-fg-muted hover:text-fg bg-transparent'
            }`}
          >
            <Icon className="h-4 w-4" />
            {label}
          </button>
        ))}
      </div>

      <AnimatePresence mode="wait">
        {activeTab === 'forex' && (
          <motion.div
            key="forex"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.2 }}
          >
            {rates.length === 0 ? (
              <EmptyState
                icon={<ArrowDownUp className="h-8 w-8 text-fg-muted" />}
                message="Döviz kuru verisi bulunamadı"
              />
            ) : (
              <motion.div
                variants={containerVariants(0.04)}
                initial="hidden"
                animate="show"
                className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
              >
                {rates.map((rate) => {
                  const CurrIcon = CURRENCY_ICONS[rate.currencyCode] || ArrowDownUp;
                  return (
                    <motion.div
                      key={rate.id}
                      variants={cardVariants}
                      className="group rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-5 card-hover transition-all duration-200 hover:border-border-hover"
                    >
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface text-fg-subtle group-hover:text-accent transition-colors">
                          <CurrIcon className="h-5 w-5" />
                        </div>
                        <div className="min-w-0">
                          <h3 className="truncate text-sm font-semibold text-fg">{rate.currencyName}</h3>
                          <span className="text-xs text-fg-muted">{rate.currencyCode}/TRY</span>
                        </div>
                      </div>

                      <div className="mt-3 space-y-1.5">
                        <div className="flex items-center justify-between rounded-lg bg-success/5 px-3 py-2">
                          <span className="text-xs text-fg-muted">Alış</span>
                          <span className="font-mono text-sm font-semibold text-success">₺{formatRate(rate.buyingRate)}</span>
                        </div>
                        <div className="flex items-center justify-between rounded-lg bg-danger/5 px-3 py-2">
                          <span className="text-xs text-fg-muted">Satış</span>
                          <span className="font-mono text-sm font-semibold text-danger">₺{formatRate(rate.sellingRate)}</span>
                        </div>
                      </div>

                      <p className="mt-2.5 text-right text-[11px] text-fg-subtle">{formatDate(rate.rateDate)}</p>
                    </motion.div>
                  );
                })}
              </motion.div>
            )}
          </motion.div>
        )}

        {activeTab === 'metals' && (
          <motion.div
            key="metals"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.2 }}
          >
            {metals.length === 0 ? (
              <EmptyState
                icon={<Gem className="h-8 w-8 text-fg-muted" />}
                message="Kıymetli maden verisi bulunamadı"
              />
            ) : (
              <motion.div
                variants={containerVariants(0.04)}
                initial="hidden"
                animate="show"
                className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
              >
                {metals.map((metal) => {
                  const iconStyle = METAL_ICON_STYLES[metal.symbol] || 'text-accent';
                  const MetalIcon = ['PAXG', 'XAUT', 'GOLD'].includes(metal.symbol) ? CircleDot : Star;
                  return (
                    <motion.div
                      key={metal.id}
                      variants={cardVariants}
                      className="group rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-5 card-hover transition-all duration-200 hover:border-border-hover"
                    >
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface">
                          <MetalIcon className={`h-5 w-5 ${iconStyle}`} />
                        </div>
                        <div className="min-w-0">
                          <h3 className="truncate text-sm font-semibold text-fg">{METAL_NAMES[metal.symbol] || metal.symbol}</h3>
                          <span className="text-xs text-fg-muted">{metal.symbol}</span>
                        </div>
                      </div>

                      <p className="mt-3 font-mono text-xl font-bold text-fg">
                        ${parseFloat(metal.priceUsd).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </p>

                      <div className={`mt-1.5 inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${
                        metal.changePercent >= 0 ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'
                      }`}>
                        {metal.changePercent >= 0 ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
                        {metal.changePercent >= 0 ? '+' : ''}{parseFloat(metal.changePercent).toFixed(2)}%
                      </div>

                      <p className="mt-2.5 text-right text-[11px] text-fg-subtle">{formatDate(metal.timestamp)}</p>
                    </motion.div>
                  );
                })}
              </motion.div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
