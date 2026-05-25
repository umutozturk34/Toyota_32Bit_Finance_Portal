import { useTranslation } from 'react-i18next';
import { Wallet, Download } from 'lucide-react';
import PageHeader from '../../../shared/components/layout/PageHeader';
import PortfolioSwitcher from './PortfolioSwitcher';

export default function PortfolioActions({
  portfolio,
  portfolios,
  loading,
  onRefresh,
  onSelectPortfolio,
  onDownloadPdf,
  pdfPending,
  pdfElapsedMs,
  hasPositions = true,
}) {
  const { t } = useTranslation();
  const pdfDisabled = pdfPending || !hasPositions;
  const pdfTitle = !hasPositions
    ? t('portfolio.actions.downloadPdfEmpty')
    : pdfPending
      ? t('portfolio.actions.downloadPdfPending')
      : t('portfolio.actions.downloadPdf');

  return (
    <div className="flex items-center justify-between gap-3 flex-wrap">
      <PageHeader
        icon={<Wallet className="h-5 w-5" />}
        title={t('portfolio.headerTitle')}
        onRefresh={onRefresh}
        loading={loading}
      />
      <div className="flex items-center gap-2 flex-wrap">
        {portfolio && (
          <button
            type="button"
            onClick={onDownloadPdf}
            disabled={pdfDisabled}
            title={pdfTitle}
            aria-disabled={pdfDisabled}
            className={`group relative flex items-center gap-2 overflow-hidden rounded-lg border px-3 py-1.5 text-[12px] font-display font-semibold tracking-tight transition-all duration-200 cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 ${
              pdfPending
                ? 'border-accent/40 bg-accent/5 text-fg'
                : 'border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:border-border-hover'
            }`}
          >
            {pdfPending && (
              <span
                aria-hidden="true"
                className="pointer-events-none absolute inset-0 bg-[linear-gradient(110deg,transparent,rgba(139,92,246,0.18),transparent)] bg-[length:200%_100%] animate-[pdfShimmer_1.4s_linear_infinite]"
                style={{
                  animation: 'pdfShimmer 1.4s linear infinite',
                }}
              />
            )}
            {pdfPending ? (
              <span className="relative inline-flex h-3.5 w-3.5 items-center justify-center">
                <span
                  aria-hidden="true"
                  className="absolute inset-0 rounded-full"
                  style={{
                    background:
                      'conic-gradient(from 0deg, #6366f1, #8b5cf6, #a78bfa, #6366f1)',
                    animation: 'pdfSpin 0.9s linear infinite',
                    WebkitMask:
                      'radial-gradient(circle, transparent 38%, #000 41%)',
                    mask: 'radial-gradient(circle, transparent 38%, #000 41%)',
                  }}
                />
              </span>
            ) : (
              <Download className="h-3.5 w-3.5" />
            )}
            <span className="relative tabular-nums">
              {pdfPending
                ? `${(pdfElapsedMs / 1000).toFixed(1)}s · ${t('portfolio.actions.downloadPdfPending')}`
                : t('portfolio.actions.downloadPdf')}
            </span>
            <style>{`
              @keyframes pdfSpin { to { transform: rotate(360deg); } }
              @keyframes pdfShimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
            `}</style>
          </button>
        )}
        {portfolios && portfolios.length > 0 && (
          <PortfolioSwitcher
            portfolios={portfolios}
            activeId={portfolio?.id}
            onSelect={onSelectPortfolio}
          />
        )}
      </div>
    </div>
  );
}
