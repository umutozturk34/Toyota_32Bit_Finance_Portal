import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Wallet, LayoutDashboard, TrendingUp as TrendingUpIcon, ShieldCheck } from 'lucide-react';
import { Check, AlertTriangle } from '../../shared/components/feedback/AnimatedIcons';
import PageHeader from '../../shared/components/layout/PageHeader';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import ProcessingSteps from '../../shared/components/feedback/ProcessingSteps';
import useProcessingAnimation from '../../shared/hooks/useProcessingAnimation';
import SummaryCards from './components/SummaryCards';
import PositionsTable from './components/PositionsTable';
import AllocationChart from './components/AllocationChart';
import PerformanceChart from './components/PerformanceChart';
import PositionFormModal from './components/PositionFormModal';
import PositionDeleteDialog from './components/PositionDeleteDialog';
import AssetDetail from './components/AssetDetail';
import {
  usePortfolioList, usePortfolioView, usePortfolioPositions,
  useCreatePortfolio, useInvalidatePortfolio,
} from './hooks/usePortfolioData';

const DEFAULT_PORTFOLIO_NAME = 'Demo Portföy';
const ONBOARDING_SUCCESS_HOLD_MS = 900;

export default function Portfolio() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const invalidatePortfolio = useInvalidatePortfolio();
  const onboardingSteps = [
    { label: t('portfolio.onboarding.steps.verifying'), duration: 600 },
    { label: t('portfolio.onboarding.steps.creating'), duration: 700 },
    { label: t('portfolio.onboarding.steps.preparing'), duration: 600 },
  ];
  const tabs = [
    { id: 'overview', label: t('portfolio.tabs.overview'), Icon: LayoutDashboard },
    { id: 'performance', label: t('portfolio.tabs.performance'), Icon: TrendingUpIcon },
  ];

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

  const [editTarget, setEditTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [onboardingPhase, setOnboardingPhase] = useState('idle');
  const createPortfolio = useCreatePortfolio();
  const { processingStep, runAnimation, reset: resetOnboarding } = useProcessingAnimation();

  const handleStartOnboarding = () => setOnboardingPhase('confirm');
  const handleCancelOnboarding = () => setOnboardingPhase('idle');

  const handleCreatePortfolio = async () => {
    setOnboardingPhase('processing');
    try {
      await Promise.all([
        createPortfolio.mutateAsync(DEFAULT_PORTFOLIO_NAME),
        runAnimation(onboardingSteps),
      ]);
      setOnboardingPhase('success');
      setTimeout(() => {
        invalidatePortfolio();
        setOnboardingPhase('idle');
      }, ONBOARDING_SUCCESS_HOLD_MS);
    } catch {
      resetOnboarding();
      setOnboardingPhase('idle');
    }
  };

  if (loading) return <LoadingState message={t('portfolio.loading')} />;
  if (error) return <ErrorState message={error} onRetry={invalidatePortfolio} />;

  if (needsOnboarding) {
    return (
      <div className="space-y-6">
        <PageHeader icon={<Wallet className="h-5 w-5" />} title={t('portfolio.headerTitle')} />
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex flex-col items-center justify-center gap-5 rounded-xl border border-border-default bg-bg-elevated card-hover backdrop-blur-md p-12 min-h-[320px]"
        >
          <AnimatePresence mode="wait">
            {onboardingPhase === 'success' && (
              <motion.div
                key="success"
                initial={{ scale: 0.92, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                exit={{ scale: 0.92, opacity: 0 }}
                transition={{ duration: 0.22, ease: [0.4, 0, 0.2, 1] }}
                className="flex flex-col items-center gap-3"
              >
                <motion.div
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  transition={{ type: 'spring', stiffness: 300, damping: 20 }}
                  className="flex items-center justify-center w-16 h-16 rounded-full bg-success/15"
                >
                  <Check className="h-8 w-8 text-success" strokeWidth={2.5} />
                </motion.div>
                <p className="text-base font-semibold text-fg">{t('portfolio.onboarding.successTitle')}</p>
                <div className="flex items-center gap-1.5 text-[11px] text-success/70">
                  <ShieldCheck className="h-3.5 w-3.5" />
                  {t('portfolio.onboarding.successHint')}
                </div>
              </motion.div>
            )}
            {onboardingPhase === 'processing' && (
              <motion.div
                key="processing"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.18 }}
                className="w-full"
              >
                <ProcessingSteps steps={onboardingSteps} currentStep={processingStep} />
              </motion.div>
            )}
            {onboardingPhase === 'confirm' && (
              <motion.div
                key="confirm"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.18, ease: [0.4, 0, 0.2, 1] }}
                className="w-full max-w-sm space-y-5"
              >
                <div className="flex flex-col items-center gap-3">
                  <div className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10">
                    <AlertTriangle className="h-6 w-6 text-warning" />
                  </div>
                  <div className="text-center space-y-1">
                    <p className="text-sm font-semibold text-fg">{t('portfolio.onboarding.confirmTitle')}</p>
                    <p className="text-xs text-fg-muted">
                      <span className="font-medium text-fg">{DEFAULT_PORTFOLIO_NAME}</span> {t('portfolio.onboarding.confirmHint')}
                    </p>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={handleCancelOnboarding}
                    className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
                  >
                    {t('common.cancel')}
                  </button>
                  <button
                    onClick={handleCreatePortfolio}
                    className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
                  >
                    <Wallet className="h-4 w-4" />
                    {t('common.confirm')}
                  </button>
                </div>
              </motion.div>
            )}
            {onboardingPhase === 'idle' && (
              <motion.div
                key="idle"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.22, ease: [0.4, 0, 0.2, 1] }}
                className="flex flex-col items-center gap-5"
              >
                <div className="flex items-center justify-center w-16 h-16 rounded-2xl bg-accent/10">
                  <Wallet className="w-8 h-8 text-accent" />
                </div>
                <div className="text-center space-y-2">
                  <h2 className="text-xl font-semibold text-fg">{t('portfolio.onboarding.idleTitle')}</h2>
                  <p className="text-sm text-fg-muted max-w-md">
                    {t('portfolio.onboarding.idleSubtitle')}
                  </p>
                </div>
                <button
                  onClick={handleStartOnboarding}
                  className="flex items-center gap-2 rounded-lg bg-accent px-6 py-2.5 text-sm font-semibold text-white transition-all hover:bg-accent-bright border-none cursor-pointer"
                >
                  <Wallet className="h-4 w-4" />
                  {t('portfolio.onboarding.startCta')}
                </button>
              </motion.div>
            )}
          </AnimatePresence>
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

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<Wallet className="h-5 w-5" />}
        title={t('portfolio.headerTitle')}
        onRefresh={invalidatePortfolio}
        loading={loading}
      />

      {summary && <SummaryCards summary={summary} portfolioId={portfolio?.id} />}

      <div className="flex gap-1 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 w-fit">
        {tabs.map(({ id, label, Icon }) => (
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
              onEditClick={setEditTarget}
              onDeleteClick={setDeleteTarget}
            />
            <AllocationChart allocation={allocation} portfolioId={portfolio?.id} />
          </div>
        )}

        {activeTab === 'performance' && portfolio && (
          <PerformanceChart portfolioId={portfolio.id} />
        )}
      </div>

      {editTarget && portfolio && (
        <PositionFormModal
          mode="edit"
          portfolioId={portfolio.id}
          position={editTarget}
          onClose={() => setEditTarget(null)}
          onComplete={invalidatePortfolio}
        />
      )}

      {deleteTarget && portfolio && (
        <PositionDeleteDialog
          portfolioId={portfolio.id}
          position={deleteTarget}
          onClose={() => setDeleteTarget(null)}
          onComplete={invalidatePortfolio}
        />
      )}
    </div>
  );
}
