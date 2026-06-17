import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { SPRING } from '../../shared/utils/animations';
import { useSearchParams } from 'react-router-dom';
import { LayoutDashboard, TrendingUp as TrendingUpIcon, Layers } from 'lucide-react';
import useNavigationBack from '../../shared/hooks/useNavigationBack';
import { useUserPreferences } from '../../shared/hooks/useUserPreferences';
import useAppStore from '../../shared/stores/useAppStore';
import { Skeleton, SkeletonChart, SkeletonStat, SkeletonList } from '../../shared/components/feedback/Skeleton';
import ErrorState from '../../shared/components/feedback/ErrorState';
import SummaryCards from './components/SummaryCards';
import PositionsTable from './components/PositionsTable';
import PositionSearchBar from './components/PositionSearchBar';
import AllocationChart from './components/AllocationChart';
import RealizedPnlChart from './components/RealizedPnlChart';
import CostBreakdownChart from './components/CostBreakdownChart';
import PnlByTypeChart from './components/PnlByTypeChart';
import PerformanceChart from './components/PerformanceChart';
import PnlBreakdownChart from './components/PnlBreakdownChart';
import AssetDetail from './components/AssetDetail';
import PortfolioActions from './components/PortfolioActions';
import DepositsList from './components/DepositsList';
import BondsList from './components/BondsList';
import FixedIncomeSummaryCard from './components/FixedIncomeSummaryCard';
import FixedIncomeChart from './components/FixedIncomeChart';
import FixedIncomePnlChart from './components/FixedIncomePnlChart';
import CouponCashflowChart from './components/CouponCashflowChart';
import PortfolioModalsHost from './components/PortfolioModalsHost';
import PortfolioOnboardingHost from './components/PortfolioOnboardingHost';
import usePortfolioPageState from './hooks/usePortfolioPageState';
import usePortfolioPdfDownload from './hooks/usePortfolioPdfDownload';
import {
  usePortfolioList, usePortfolioView, usePortfolioPositions,
  useInvalidatePortfolio, useBackfillStatus, useReopenPosition,
} from './hooks/usePortfolioData';
import { useReopenDerivativePosition } from './hooks/useDerivativePositions';

// Stable empty-array reference so the memoized AllocationChart isn't re-rendered by a fresh `[]` each render
// while allocation data is absent (React Query keeps the populated array referentially stable via structural sharing).
const EMPTY_ALLOCATION = [];

export default function Portfolio() {
  const { t } = useTranslation();
  const goBack = useNavigationBack('/portfolio');
  const invalidatePortfolio = useInvalidatePortfolio();
  const { preferences } = useUserPreferences();
  const displayCurrency = useAppStore((s) => s.displayCurrency);
  // The PDF supports TRY/USD/EUR; 'ORIGINAL' (or any unknown value) renders each lot in its own
  // currency on screen, which the report can't mirror, so it falls back to TRY.
  const pdfCurrency = ['TRY', 'USD', 'EUR'].includes(displayCurrency) ? displayCurrency : 'TRY';

  const tabs = [
    { id: 'overview', label: t('portfolio.tabs.overview'), Icon: LayoutDashboard },
    { id: 'performance', label: t('portfolio.tabs.performance'), Icon: TrendingUpIcon },
    { id: 'pnl', label: t('portfolio.tabs.pnl'), Icon: Layers },
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
  const portfolioType = portfolio?.type === 'FIXED' ? 'fixed' : 'spot';
  // The sidebar's "New portfolio" entry deep-links here with ?new=1 so a portfolio can be created without first
  // navigating in; the switcher opens straight into create mode and we drop the flag so it fires once.
  const wantsCreate = searchParams.get('new') === '1';
  const clearCreateFlag = () => setSearchParams((prev) => {
    const next = new URLSearchParams(prev);
    next.delete('new');
    return next;
  }, { replace: true });
  const setActivePortfolio = (id) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('portfolio', String(id));
      next.delete('asset');
      next.delete('tab');
      return next;
    });
  };

  const setActivePortfolioId = useAppStore((s) => s.setActivePortfolioId);
  useEffect(() => {
    if (portfolio?.id != null) setActivePortfolioId(portfolio.id);
  }, [portfolio?.id, setActivePortfolioId]);

  const { data: view, isLoading: viewLoading, error: viewError } = usePortfolioView(portfolio?.id);
  const summary = view?.summary ?? null;
  const allocation = view?.allocation ?? EMPTY_ALLOCATION;
  const viewPositions = view?.positions?.content ?? [];
  const backfill = useBackfillStatus(portfolio?.id);

  const loading = listLoading || (portfolio && viewLoading);
  const error = (!is404 && listError?.response?.data?.message) || viewError?.response?.data?.message || null;

  const { download: downloadPdf, isPending: pdfPending, elapsedMs: pdfElapsedMs } = usePortfolioPdfDownload({
    portfolio,
    currency: pdfCurrency,
    theme: preferences?.theme,
    locale: preferences?.language,
  });

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

  const pageState = usePortfolioPageState({ selectedAssetCode, goBack, setSearchParams });
  const {
    pendingAsset,
    editTarget, setEditTarget,
    deleteTarget, setDeleteTarget,
    closeTarget, setCloseTarget,
    sellTarget, setSellTarget,
    onboardingPhase, setOnboardingPhase,
    onboardingName, setOnboardingName,
    selectAsset,
    hasActiveDialog,
  } = pageState;

  const selectedAsset = (pendingAsset && pendingAsset.assetCode === selectedAssetCode ? pendingAsset : null)
    || (selectedAssetCode
      ? (viewPositions.find(p => p.assetCode === selectedAssetCode) || searchedPositions?.content?.[0] || null)
      : null);

  const reopenSpot = useReopenPosition(portfolio?.id);
  const reopenViop = useReopenDerivativePosition(portfolio?.id);

  if (loading) {
    return (
      <div className="space-y-6 py-2">
        <div className="flex items-center justify-between">
          <Skeleton w="12rem" h="2rem" className="rounded-xl" />
          <Skeleton w="8rem" h="2.4rem" className="rounded-xl" />
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <SkeletonStat /><SkeletonStat /><SkeletonStat /><SkeletonStat />
        </div>
        <SkeletonChart h="18rem" />
        <SkeletonList rows={5} cols={4} />
      </div>
    );
  }
  if (error) return <ErrorState message={error} onRetry={invalidatePortfolio} />;

  if (needsOnboarding) {
    return (
      <PortfolioOnboardingHost
        phase={onboardingPhase}
        setPhase={setOnboardingPhase}
        name={onboardingName}
        setName={setOnboardingName}
      />
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

  return (
    <>
      {portfolioType === 'spot' && selectedAsset ? (
        <AssetDetail
          portfolioId={portfolio.id}
          asset={selectedAsset}
          onBack={() => selectAsset(null)}
          onEditLot={setEditTarget}
          onDeleteLot={setDeleteTarget}
          onSellLot={handleSellOrClose}
          onReopenLot={handleReopen}
          hasActiveDialog={hasActiveDialog}
        />
      ) : portfolioType === 'fixed' ? (
        <div className="space-y-6">
          <PortfolioActions
            portfolio={portfolio}
            portfolios={portfolios}
            loading={loading}
            onRefresh={invalidatePortfolio}
            onSelectPortfolio={setActivePortfolio}
            hasPositions={false}
            showExtras={false}
            autoCreatePortfolio={wantsCreate}
            onAutoCreateConsumed={clearCreateFlag}
          />

          {portfolio?.id && (
            <div className="space-y-6">
              <FixedIncomeSummaryCard portfolioId={portfolio.id} />
              <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 items-start">
                <FixedIncomeChart portfolioId={portfolio.id} />
                <CouponCashflowChart portfolioId={portfolio.id} />
              </div>
              <FixedIncomePnlChart portfolioId={portfolio.id} />
            </div>
          )}

          <div className="grid grid-cols-1 2xl:grid-cols-2 gap-6 items-start">
            <DepositsList portfolioId={portfolio?.id} />
            <BondsList portfolioId={portfolio?.id} />
          </div>
        </div>
      ) : (
        <div className="space-y-6">
          <PortfolioActions
            portfolio={portfolio}
            portfolios={portfolios}
            loading={loading}
            onRefresh={invalidatePortfolio}
            onSelectPortfolio={setActivePortfolio}
            onDownloadPdf={downloadPdf}
            pdfPending={pdfPending}
            pdfElapsedMs={pdfElapsedMs}
            hasPositions={viewPositions.length > 0}
            autoCreatePortfolio={wantsCreate}
            onAutoCreateConsumed={clearCreateFlag}
          />

          {summary && <SummaryCards summary={summary} portfolioId={portfolio?.id} />}

          <div className="flex max-w-full gap-1 overflow-x-auto rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 w-fit">
            {tabs.map(({ id, label, Icon }) => (
              <button
                key={id}
                onClick={() => setActiveTab(id)}
                className="relative flex shrink-0 items-center gap-1.5 rounded-lg px-3 sm:px-4 py-2 text-xs font-medium transition-all border-none cursor-pointer bg-transparent"
              >
                {activeTab === id && (
                  <motion.span
                    layoutId="portfolio-tab"
                    className="absolute inset-0 rounded-lg bg-accent/15"
                    transition={SPRING.tab}
                  />
                )}
                <Icon className={`relative z-10 h-3.5 w-3.5 ${activeTab === id ? 'text-accent' : 'text-fg-muted'}`} />
                <span className={`relative z-10 ${activeTab === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                  {label}
                </span>
              </button>
            ))}
          </div>

          <motion.div
            animate={{ opacity: activeTab === 'overview' ? 1 : 0 }}
            transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
            style={{ display: activeTab === 'overview' ? 'block' : 'none' }}
          >
            <div className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6 min-w-0">
                <AllocationChart allocation={allocation} portfolioId={portfolio?.id} />
                <RealizedPnlChart portfolioId={portfolio?.id} />
              </div>
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6 min-w-0">
                <CostBreakdownChart portfolioId={portfolio?.id} />
                <PnlByTypeChart portfolioId={portfolio?.id} />
              </div>
              <PositionSearchBar />
              <div className="min-w-0">
                <PositionsTable
                  portfolioId={portfolio?.id}
                  backfill={backfill}
                  onAssetClick={selectAsset}
                  onEditClick={setEditTarget}
                  onDeleteClick={setDeleteTarget}
                  onCloseClick={setCloseTarget}
                  onSellClick={setSellTarget}
                />
              </div>
            </div>
          </motion.div>

          {/* Toggle visibility (not mount) so the chart's React Query cache and rendered canvas survive tab
              switches — conditional unmounting made the tab flash blank on every re-entry. Opacity cross-fades on
              show so switching reads smooth instead of an instant cut, without ever remounting the chart. */}
          <motion.div
            animate={{ opacity: portfolio && activeTab === 'performance' ? 1 : 0 }}
            transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
            style={{ display: portfolio && activeTab === 'performance' ? 'block' : 'none' }}
          >
            {portfolio && <PerformanceChart portfolioId={portfolio.id} backfill={backfill} />}
          </motion.div>

          <motion.div
            animate={{ opacity: portfolio && activeTab === 'pnl' ? 1 : 0 }}
            transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
            style={{ display: portfolio && activeTab === 'pnl' ? 'block' : 'none' }}
          >
            {portfolio && <PnlBreakdownChart portfolioId={portfolio.id} />}
          </motion.div>
        </div>
      )}

      <PortfolioModalsHost
        portfolio={portfolio}
        editTarget={editTarget}
        setEditTarget={setEditTarget}
        closeTarget={closeTarget}
        setCloseTarget={setCloseTarget}
        sellTarget={sellTarget}
        setSellTarget={setSellTarget}
        deleteTarget={deleteTarget}
        setDeleteTarget={setDeleteTarget}
        invalidatePortfolio={invalidatePortfolio}
      />
    </>
  );
}
