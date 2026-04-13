import { useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Wallet, ShieldCheck, LayoutDashboard, History } from 'lucide-react';
import { Check, RefreshCw, Loader2, TrendingUp, AlertTriangle } from '../../shared/components/AnimatedIcons';
import { useTheme } from '../../shared/context/ThemeContext';
import PageHeader from '../../shared/components/PageHeader';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import SummaryCards from './SummaryCards';
import PositionsTable from './PositionsTable';
import AllocationChart from './AllocationChart';
import PerformanceChart from './PerformanceChart';
import TransactionHistory from './TransactionHistory';
import SellModal from './SellModal';
import AssetDetail from './AssetDetail';
import { portfolioService } from './portfolioService';
import { usePortfolioList, usePortfolioView, usePortfolioPositions, useInvalidatePortfolio } from './usePortfolioData';

const ONBOARDING_STEPS = [
  { label: 'Hesap oluşturuluyor...', duration: 900 },
  { label: 'Demo bakiye yükleniyor...', duration: 700 },
  { label: 'Portföy hazırlanıyor...', duration: 800 },
];

export default function Portfolio() {
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const invalidatePortfolio = useInvalidatePortfolio();

  const { data: portfolios, isLoading: listLoading, error: listError } = usePortfolioList();
  const portfolio = portfolios?.[0] ?? null;
  const is404 = listError?.response?.status === 404;
  const needsOnboarding = !listLoading && (is404 || !portfolios || portfolios.length === 0);

  const { data: view, isLoading: viewLoading, error: viewError } = usePortfolioView(portfolio?.id);
  const summary = view?.summary ?? null;
  const allocation = view?.allocation ?? [];
  const viewPositions = view?.positions?.content ?? [];

  const loading = listLoading || (portfolio && viewLoading);
  const error = (!is404 && listError?.response?.data?.message) || viewError?.response?.data?.message || null;

  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') || 'overview';
  const selectedAssetCode = searchParams.get('asset');

  const setActiveTab = (tab) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('asset');
      if (tab === 'overview') next.delete('tab');
      else next.set('tab', tab);
      return next;
    }, { replace: true });
  };

  const { data: searchedPositions } = usePortfolioPositions(
    selectedAssetCode ? portfolio?.id : null,
    selectedAssetCode ? { search: selectedAssetCode, size: 1 } : {}
  );
  const selectedAsset = selectedAssetCode
    ? (viewPositions.find(p => p.assetCode === selectedAssetCode) || searchedPositions?.content?.[0] || null)
    : null;

  const setSelectedAsset = (asset) => {
    if (asset) {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('asset', asset.assetCode);
        return next;
      }, { replace: false });
    } else {
      navigate(-1);
    }
  };
  const [sellTarget, setSellTarget] = useState(null);
  const [onboardingConfirming, setOnboardingConfirming] = useState(false);
  const [onboarding, setOnboarding] = useState(false);
  const [onboardingStep, setOnboardingStep] = useState(-1);
  const [onboardingSuccess, setOnboardingSuccess] = useState(false);
  const stepTimers = useRef([]);

  const runOnboardingAnimation = () => {
    return new Promise((resolve) => {
      let elapsed = 0;
      ONBOARDING_STEPS.forEach((step, idx) => {
        const timer = setTimeout(() => setOnboardingStep(idx), elapsed);
        stepTimers.current.push(timer);
        elapsed += step.duration;
      });
      const finalTimer = setTimeout(resolve, elapsed);
      stepTimers.current.push(finalTimer);
    });
  };

  const handleOnboarding = async () => {
    setOnboardingConfirming(false);
    setOnboarding(true);
    setOnboardingStep(0);
    try {
      await Promise.all([
        portfolioService.initialize(),
        runOnboardingAnimation(),
      ]);
      setOnboardingSuccess(true);
      setTimeout(() => {
        invalidatePortfolio();
        setOnboarding(false);
        setOnboardingSuccess(false);
        setOnboardingStep(-1);
      }, 2000);
    } catch (err) {
      setOnboarding(false);
      setOnboardingStep(-1);
    }
  };

  const handleSellComplete = () => {
    setSellTarget(null);
    invalidatePortfolio();
  };

  if (loading) return <LoadingState message="Portföy yükleniyor..." />;
  if (error) return <ErrorState message={error} onRetry={invalidatePortfolio} />;

  if (needsOnboarding) {
    return (
      <div className="space-y-6">
        <PageHeader
          icon={<Wallet className="h-5 w-5" />}
          title="Portföy"
          onRefresh={invalidatePortfolio}
          loading={loading}
        />
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative flex flex-col items-center justify-center gap-5 rounded-xl border border-border-default bg-bg-elevated card-hover backdrop-blur-md p-12 overflow-hidden"
        >
          {isDark && (
            <div
              className="pointer-events-none absolute inset-0"
              style={{ background: 'radial-gradient(circle at 50% 30%, rgba(99,102,241,0.08) 0%, transparent 60%)' }}
            />
          )}

          {onboardingSuccess ? (
            <motion.div
              initial={{ scale: 0.8, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="relative flex flex-col items-center gap-3 py-4"
            >
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 300, damping: 20 }}
                className="flex items-center justify-center w-16 h-16 rounded-full bg-success/15"
              >
                <Check className="h-8 w-8 text-success" strokeWidth={2.5} />
              </motion.div>
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.2 }}
                className="text-center space-y-1"
              >
                <p className="text-base font-semibold text-fg">İşleminiz onaylandı</p>
                <p className="text-sm text-fg-muted">Demo portföyünüz başarıyla oluşturuldu</p>
              </motion.div>
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.35 }}
                className="flex items-center gap-1.5 text-xs text-success/70"
              >
                <ShieldCheck className="h-3.5 w-3.5" />
                1.000.000 TL bakiye yüklendi
              </motion.div>
            </motion.div>
          ) : onboarding ? (
            <div className="relative flex flex-col items-center gap-5 py-4">
              <RefreshCw className="h-8 w-8 text-accent animate-spin" />
              <div className="space-y-3 w-full max-w-xs">
                {ONBOARDING_STEPS.map((step, idx) => (
                  <motion.div
                    key={idx}
                    initial={{ opacity: 0, x: -8 }}
                    animate={{
                      opacity: onboardingStep >= idx ? 1 : 0.3,
                      x: 0,
                    }}
                    transition={{ duration: 0.3, delay: idx * 0.1 }}
                    className="flex items-center gap-2.5"
                  >
                    {onboardingStep > idx ? (
                      <Check className="h-3.5 w-3.5 text-success shrink-0" />
                    ) : onboardingStep === idx ? (
                      <Loader2 className="h-3.5 w-3.5 text-accent animate-spin shrink-0" />
                    ) : (
                      <div className="h-3.5 w-3.5 rounded-full border border-border-default shrink-0" />
                    )}
                    <span className={`text-xs font-medium ${onboardingStep >= idx ? 'text-fg' : 'text-fg-subtle'}`}>
                      {step.label}
                    </span>
                  </motion.div>
                ))}
              </div>
            </div>
          ) : onboardingConfirming ? (
            <motion.div
              initial={{ opacity: 0, y: 5 }}
              animate={{ opacity: 1, y: 0 }}
              className="relative flex flex-col items-center gap-5 py-4 w-full max-w-sm"
            >
              <div className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10">
                <AlertTriangle className="h-6 w-6 text-warning" />
              </div>
              <div className="text-center space-y-1">
                <p className="text-sm font-semibold text-fg">İşleminizi onaylıyor musunuz?</p>
                <p className="text-xs text-fg-muted">Demo portföy oluşturulacak</p>
              </div>
              <div className="w-full rounded-xl border border-border-default bg-bg-base px-4 py-3 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-fg-muted">Başlangıç Bakiyesi</span>
                  <span className="font-mono font-medium text-fg">1.000.000 ₺</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-fg-muted">Portföy Türü</span>
                  <span className="font-medium text-fg">Demo</span>
                </div>
                <div className="flex items-center justify-between text-xs border-t border-border-default pt-2">
                  <span className="text-fg-muted">Desteklenen Varlıklar</span>
                  <span className="font-medium text-fg">Kripto, Hisse, Döviz, Fon</span>
                </div>
              </div>
              <div className="flex gap-2 w-full">
                <button
                  onClick={() => setOnboardingConfirming(false)}
                  className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
                >
                  Vazgeç
                </button>
                <button
                  onClick={handleOnboarding}
                  className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
                >
                  <Wallet className="h-4 w-4" />
                  Onayla
                </button>
              </div>
            </motion.div>
          ) : (
            <>
              <div className="relative flex items-center justify-center w-16 h-16 rounded-2xl bg-accent/10">
                <Wallet className="w-8 h-8 text-accent" />
              </div>
              <div className="relative text-center space-y-2">
                <h2 className="text-xl font-semibold text-fg">Demo Portföyünüzü Oluşturun</h2>
                <p className="text-sm text-fg-muted max-w-md">
                  1.000.000 TL demo bakiye ile sanal portföyünüzü başlatın. Kripto, hisse, döviz ve fon alım-satımı yapabilirsiniz.
                </p>
              </div>
              <button
                onClick={() => setOnboardingConfirming(true)}
                className="relative flex items-center gap-2 rounded-lg bg-accent px-6 py-2.5 text-sm font-semibold text-white transition-all hover:bg-accent-bright border-none cursor-pointer"
              >
                Portföyü Başlat
              </button>
            </>
          )}
        </motion.div>
      </div>
    );
  }

  if (selectedAsset) {
    return (
      <AssetDetail
        portfolioId={portfolio.id}
        asset={selectedAsset}
        onBack={() => setSelectedAsset(null)}
      />
    );
  }

  const TABS = [
    { id: 'overview', label: 'Genel Bakış', Icon: LayoutDashboard },
    { id: 'performance', label: 'Performans', Icon: TrendingUp },
    { id: 'transactions', label: 'İşlemler', Icon: History },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<Wallet className="h-5 w-5" />}
        title="Portföy"
        onRefresh={invalidatePortfolio}
        loading={loading}
      />

      {summary && <SummaryCards summary={summary} portfolioId={portfolio?.id} />}

      <div className="flex gap-1 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 w-fit">
        {TABS.map(({ id, label, Icon }) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className="relative flex items-center gap-1.5 rounded-lg px-4 py-2 text-xs font-medium transition-all border-none cursor-pointer bg-transparent"
          >
            {activeTab === id && (
              <motion.span
                layoutId="portfolio-tab"
                className="absolute inset-0 rounded-lg bg-accent/15"
                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
              />
            )}
            <Icon className={`relative z-10 h-3.5 w-3.5 ${activeTab === id ? 'text-accent' : 'text-fg-muted'}`} />
            <span className={`relative z-10 ${activeTab === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
              {label}
            </span>
          </button>
        ))}
      </div>

      <div className="min-h-[400px]">
        {activeTab === 'overview' && (
          <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">
            <PositionsTable
              portfolioId={portfolio?.id}
              onAssetClick={setSelectedAsset}
              onSellClick={setSellTarget}
            />
            <AllocationChart allocation={allocation} portfolioId={portfolio?.id} />
          </div>
        )}

        {activeTab === 'performance' && portfolio && (
          <PerformanceChart portfolioId={portfolio.id} />
        )}

        {activeTab === 'transactions' && (
          <TransactionHistory portfolioId={portfolio?.id} />
        )}
      </div>

      {sellTarget && portfolio && (
        <SellModal
          portfolioId={portfolio.id}
          position={sellTarget}
          onClose={() => setSellTarget(null)}
          onComplete={handleSellComplete}
        />
      )}
    </div>
  );
}
