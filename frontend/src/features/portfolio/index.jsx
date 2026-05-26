import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useSearchParams } from 'react-router-dom';
import { LayoutDashboard, TrendingUp as TrendingUpIcon } from 'lucide-react';
import useNavigationBack from '../../shared/hooks/useNavigationBack';
import { useUserPreferences } from '../../shared/hooks/useUserPreferences';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import SummaryCards from './components/SummaryCards';
import PositionsTable from './components/PositionsTable';
import AllocationChart from './components/AllocationChart';
import RealizedPnlChart from './components/RealizedPnlChart';
import PerformanceChart from './components/PerformanceChart';
import AssetDetail from './components/AssetDetail';
import PortfolioActions from './components/PortfolioActions';
import PortfolioModalsHost from './components/PortfolioModalsHost';
import PortfolioOnboardingHost from './components/PortfolioOnboardingHost';
import usePortfolioPageState from './hooks/usePortfolioPageState';
import usePortfolioPdfDownload from './hooks/usePortfolioPdfDownload';
import {
  usePortfolioList, usePortfolioView, usePortfolioPositions,
  useInvalidatePortfolio, useBackfillStatus, useReopenPosition,
} from './hooks/usePortfolioData';
import { useReopenDerivativePosition } from './hooks/useDerivativePositions';

export default function Portfolio() {
  const { t } = useTranslation();
  const goBack = useNavigationBack('/portfolio');
  const invalidatePortfolio = useInvalidatePortfolio();
  const { preferences } = useUserPreferences();

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

  const { download: downloadPdf, isPending: pdfPending, elapsedMs: pdfElapsedMs } = usePortfolioPdfDownload({
    portfolio,
    currency: 'TRY',
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

  if (loading) return <LoadingState message={t('portfolio.loading')} />;
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
          onBack={() => selectAsset(null)}
          onEditLot={setEditTarget}
          onDeleteLot={setDeleteTarget}
          onSellLot={handleSellOrClose}
          onReopenLot={handleReopen}
          hasActiveDialog={hasActiveDialog}
        />
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

          <div style={{ display: activeTab === 'overview' ? 'block' : 'none' }}>
            <div className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <AllocationChart allocation={allocation} portfolioId={portfolio?.id} />
                <RealizedPnlChart portfolioId={portfolio?.id} />
              </div>
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
          </div>

          {portfolio && (
            <div style={{ display: activeTab === 'performance' ? 'block' : 'none' }}>
              <PerformanceChart portfolioId={portfolio.id} backfill={backfill} />
            </div>
          )}
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
