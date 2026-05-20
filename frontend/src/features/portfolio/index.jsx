import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { useSearchParams } from 'react-router-dom';
import useNavigationBack from '../../shared/hooks/useNavigationBack';
import { Wallet, LayoutDashboard, TrendingUp as TrendingUpIcon, ShieldCheck, Sparkles } from 'lucide-react';
import { Check, AlertTriangle } from '../../shared/components/feedback/AnimatedIcons';
import PageHeader from '../../shared/components/layout/PageHeader';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import ProcessingSteps from '../../shared/components/feedback/ProcessingSteps';
import Card from '../../shared/components/card';
import useProcessingAnimation from '../../shared/hooks/useProcessingAnimation';
import SummaryCards from './components/SummaryCards';
import PositionsTable from './components/PositionsTable';
import AllocationChart from './components/AllocationChart';
import RealizedPnlChart from './components/RealizedPnlChart';
import PerformanceChart from './components/PerformanceChart';
import PositionFormModal from './components/PositionFormModal';
import PositionDeleteDialog from './components/PositionDeleteDialog';
import CloseDerivativePositionDialog from './components/CloseDerivativePositionDialog';
import SellPositionDialog from './components/SellPositionDialog';
import PortfolioSwitcher from './components/PortfolioSwitcher';
import EditDerivativePositionModal from './components/EditDerivativePositionModal';
import AssetDetail from './components/AssetDetail';
import {
  usePortfolioList, usePortfolioView, usePortfolioPositions,
  useCreatePortfolio, useInvalidatePortfolio, useBackfillStatus,
  useReopenPosition,
} from './hooks/usePortfolioData';
import { useReopenDerivativePosition } from './hooks/useDerivativePositions';

const ONBOARDING_SUCCESS_HOLD_MS = 900;

export default function Portfolio() {
  const { t } = useTranslation();
  const goBack = useNavigationBack('/portfolio');
  const invalidatePortfolio = useInvalidatePortfolio();
  const defaultPortfolioName = t('portfolio.onboarding.defaultName');
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
  const is404 = listError?.response?.status === 404;
  const needsOnboarding = !listLoading && (is404 || !portfolios || portfolios.length === 0);

  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') || 'overview';
  const selectedAssetCode = searchParams.get('asset');
  const urlPortfolioId = searchParams.get('portfolio');
  const portfolio = (portfolios ?? []).find((p) => String(p.id) === urlPortfolioId)
    ?? portfolios?.[0]
    ?? null;
  const setActivePortfolio = (id) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('portfolio', String(id));
      next.delete('asset');
      next.delete('tab');
      return next;
    });
  };

  const { data: view, isLoading: viewLoading, error: viewError } = usePortfolioView(portfolio?.id);
  const summary = view?.summary ?? null;
  const allocation = view?.allocation ?? [];
  const viewPositions = view?.positions?.content ?? [];
  const backfill = useBackfillStatus(portfolio?.id);

  const loading = listLoading || (portfolio && viewLoading);
  const error = (!is404 && listError?.response?.data?.message) || viewError?.response?.data?.message || null;

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
  const [pendingAsset, setPendingAsset] = useState(null);
  const [trackedAssetCode, setTrackedAssetCode] = useState(selectedAssetCode);

  if (selectedAssetCode !== trackedAssetCode) {
    setTrackedAssetCode(selectedAssetCode);
    if (!selectedAssetCode) setPendingAsset(null);
  }

  const selectedAsset = (pendingAsset && pendingAsset.assetCode === selectedAssetCode ? pendingAsset : null)
    || (selectedAssetCode
      ? (viewPositions.find(p => p.assetCode === selectedAssetCode) || searchedPositions?.content?.[0] || null)
      : null);

  const setSelectedAsset = (asset) => {
    if (asset) {
      setPendingAsset(asset);
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('asset', asset.assetCode);
        return next;
      }, { replace: false });
    } else {
      setPendingAsset(null);
      goBack();
    }
  };

  const [editTarget, setEditTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [closeTarget, setCloseTarget] = useState(null);
  const [sellTarget, setSellTarget] = useState(null);
  const [onboardingPhase, setOnboardingPhase] = useState('idle');
  const createPortfolio = useCreatePortfolio();
  const reopenSpot = useReopenPosition(portfolio?.id);
  const reopenViop = useReopenDerivativePosition(portfolio?.id);
  const { processingStep, runAnimation, reset: resetOnboarding } = useProcessingAnimation();

  const handleStartOnboarding = () => setOnboardingPhase('confirm');
  const handleCancelOnboarding = () => setOnboardingPhase('idle');

  const handleCreatePortfolio = async () => {
    setOnboardingPhase('processing');
    try {
      await Promise.all([
        createPortfolio.mutateAsync(defaultPortfolioName),
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
        <Card
          as={motion.div}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          variant="elevated"
          radius="xl"
          padding="xl"
          backdropBlur
          className="flex flex-col items-center justify-center gap-5 min-h-[320px]"
        >
          <AnimatePresence mode="wait">
            {onboardingPhase === 'success' && (
              <motion.div
                key="success"
                initial={{ scale: 0.92, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                exit={{ scale: 0.92, opacity: 0 }}
                transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
                className="flex flex-col items-center gap-3 relative"
              >
                <motion.div
                  className="absolute top-0 w-32 h-32 rounded-full bg-success/20 blur-3xl pointer-events-none"
                  initial={{ scale: 0, opacity: 0 }}
                  animate={{ scale: [0, 1.4, 1], opacity: [0, 0.9, 0.5] }}
                  transition={{ duration: 1, ease: 'easeOut' }}
                />
                <div className="relative">
                  <motion.div
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    transition={{ type: 'spring', stiffness: 300, damping: 20 }}
                    className="flex items-center justify-center w-16 h-16 rounded-full bg-success/15"
                  >
                    <Check className="h-8 w-8 text-success" strokeWidth={2.5} />
                  </motion.div>
                  {[0, 1, 2, 3, 4, 5].map((i) => {
                    const angle = (i / 6) * Math.PI * 2;
                    return (
                      <motion.span
                        key={i}
                        className="absolute top-1/2 left-1/2 w-1.5 h-1.5 rounded-full bg-success/60"
                        initial={{ x: 0, y: 0, opacity: 0, scale: 0 }}
                        animate={{
                          x: Math.cos(angle) * 38,
                          y: Math.sin(angle) * 38,
                          opacity: [0, 1, 0],
                          scale: [0, 1, 0.4],
                        }}
                        transition={{ duration: 0.7, delay: 0.18 + i * 0.04, ease: 'easeOut' }}
                      />
                    );
                  })}
                </div>
                <motion.p
                  className="text-base font-semibold text-fg"
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.18, duration: 0.25 }}
                >
                  {t('portfolio.onboarding.successTitle')}
                </motion.p>
                <motion.div
                  className="flex items-center gap-1.5 text-[11px] text-success/70"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: 0.28, duration: 0.25 }}
                >
                  <ShieldCheck className="h-3.5 w-3.5" />
                  {t('portfolio.onboarding.successHint')}
                </motion.div>
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
                transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
                className="w-full max-w-sm space-y-5"
              >
                <div className="flex flex-col items-center gap-3">
                  <motion.div
                    className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10"
                    animate={{ scale: [1, 1.08, 1] }}
                    transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
                  >
                    <AlertTriangle className="h-6 w-6 text-warning" />
                  </motion.div>
                  <motion.div
                    className="text-center space-y-1"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: 0.1, duration: 0.25 }}
                  >
                    <p className="text-sm font-semibold text-fg">{t('portfolio.onboarding.confirmTitle')}</p>
                    <p className="text-xs text-fg-muted">
                      <span className="font-medium text-fg">{defaultPortfolioName}</span> {t('portfolio.onboarding.confirmHint')}
                    </p>
                  </motion.div>
                </div>
                <motion.div
                  className="flex gap-2"
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.18, duration: 0.25 }}
                >
                  <motion.button
                    onClick={handleCancelOnboarding}
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.97 }}
                    className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-colors cursor-pointer"
                  >
                    {t('common.cancel')}
                  </motion.button>
                  <motion.button
                    onClick={handleCreatePortfolio}
                    whileHover={{ scale: 1.02, y: -1 }}
                    whileTap={{ scale: 0.97 }}
                    className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-accent shadow-md shadow-accent/20 hover:bg-accent-bright transition-colors border-none cursor-pointer"
                  >
                    <Wallet className="h-4 w-4" />
                    {t('common.confirm')}
                  </motion.button>
                </motion.div>
              </motion.div>
            )}
            {onboardingPhase === 'idle' && (
              <motion.div
                key="idle"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
                className="flex flex-col items-center gap-5 relative"
              >
                <motion.div
                  className="absolute -top-4 w-32 h-32 rounded-full bg-accent/15 blur-3xl pointer-events-none"
                  animate={{ scale: [1, 1.15, 1], opacity: [0.6, 0.85, 0.6] }}
                  transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
                />
                <motion.div
                  className="relative flex items-center justify-center w-16 h-16 rounded-2xl bg-accent/10"
                  animate={{ y: [0, -4, 0] }}
                  transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
                >
                  <Wallet className="w-8 h-8 text-accent" />
                  <motion.span
                    className="absolute -top-1 -right-1"
                    animate={{ rotate: [0, 12, -8, 0], scale: [1, 1.15, 0.95, 1] }}
                    transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
                  >
                    <Sparkles className="w-3.5 h-3.5 text-warning" />
                  </motion.span>
                </motion.div>
                <motion.div
                  className="text-center space-y-2"
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.12, duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
                >
                  <h2 className="text-xl font-semibold text-fg">{t('portfolio.onboarding.idleTitle')}</h2>
                  <p className="text-sm text-fg-muted max-w-md">
                    {t('portfolio.onboarding.idleSubtitle')}
                  </p>
                </motion.div>
                <motion.button
                  onClick={handleStartOnboarding}
                  whileHover={{ scale: 1.04, y: -1 }}
                  whileTap={{ scale: 0.96 }}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.22, duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
                  className="flex items-center gap-2 rounded-lg bg-accent px-6 py-2.5 text-sm font-semibold text-white shadow-lg shadow-accent/20 hover:bg-accent-bright transition-colors border-none cursor-pointer"
                >
                  <Wallet className="h-4 w-4" />
                  {t('portfolio.onboarding.startCta')}
                </motion.button>
              </motion.div>
            )}
          </AnimatePresence>
        </Card>
      </div>
    );
  }

  const handleSellOrClose = (lot) => {
    if (lot.assetType === 'VIOP') setCloseTarget(lot);
    else setSellTarget(lot);
  };
  const handleReopen = (lot) => {
    if (lot.assetType === 'VIOP') reopenViop.mutate(lot.id);
    else reopenSpot.mutate(lot.id);
  };
  const selectedLots = selectedAsset
    ? viewPositions.filter(
        (p) => p.assetCode === selectedAsset.assetCode && p.assetType === selectedAsset.assetType
      )
    : [];

  return (
    <>
      {selectedAsset ? (
        <AssetDetail
          portfolioId={portfolio.id}
          asset={selectedAsset}
          lots={selectedLots}
          onBack={() => setSelectedAsset(null)}
          onEditLot={setEditTarget}
          onDeleteLot={setDeleteTarget}
          onSellLot={handleSellOrClose}
          onReopenLot={handleReopen}
          hasActiveDialog={Boolean(deleteTarget || editTarget || sellTarget || closeTarget)}
        />
      ) : (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <PageHeader
          icon={<Wallet className="h-5 w-5" />}
          title={t('portfolio.headerTitle')}
          onRefresh={invalidatePortfolio}
          loading={loading}
        />
        {portfolios && portfolios.length > 0 && (
          <PortfolioSwitcher
            portfolios={portfolios}
            activeId={portfolio?.id}
            onSelect={setActivePortfolio}
          />
        )}
      </div>

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

      <div>
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <AllocationChart allocation={allocation} portfolioId={portfolio?.id} />
              <RealizedPnlChart portfolioId={portfolio?.id} />
            </div>
            <div className="min-w-0">
              <PositionsTable
                portfolioId={portfolio?.id}
                backfill={backfill}
                onAssetClick={setSelectedAsset}
                onEditClick={setEditTarget}
                onDeleteClick={setDeleteTarget}
                onCloseClick={setCloseTarget}
                onSellClick={setSellTarget}
              />
            </div>
          </div>
        )}

        {activeTab === 'performance' && portfolio && (
          <PerformanceChart portfolioId={portfolio.id} backfill={backfill} />
        )}
      </div>
    </div>
      )}

      {editTarget && portfolio && editTarget.assetType === 'VIOP' && (
        <EditDerivativePositionModal
          portfolioId={portfolio.id}
          position={editTarget}
          onClose={() => { setEditTarget(null); invalidatePortfolio(); }}
        />
      )}

      {editTarget && portfolio && editTarget.assetType !== 'VIOP' && (
        <PositionFormModal
          mode="edit"
          portfolioId={portfolio.id}
          position={editTarget}
          onClose={() => setEditTarget(null)}
          onComplete={invalidatePortfolio}
        />
      )}

      {closeTarget && portfolio && (
        <CloseDerivativePositionDialog
          portfolioId={portfolio.id}
          position={closeTarget}
          onClose={() => { setCloseTarget(null); invalidatePortfolio(); }}
        />
      )}

      {sellTarget && portfolio && (
        <SellPositionDialog
          portfolioId={portfolio.id}
          position={sellTarget}
          onClose={() => setSellTarget(null)}
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
    </>
  );
}
