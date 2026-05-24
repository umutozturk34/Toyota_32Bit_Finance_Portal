import { useCallback, useState } from 'react';
import api from '../../../shared/services/api';

function captureChart(refMap, key) {
  const cap = refMap?.[key]?.current?.capture?.();
  return typeof cap === 'string' && cap.startsWith('data:image/') ? cap : null;
}

function buildPayload({ portfolio, summary, positions, allocation, chartRefs, currency, theme, locale }) {
  const charts = {};
  ['allocation', 'realizedPnl', 'performance'].forEach((k) => {
    const v = captureChart(chartRefs, k);
    if (v) charts[k] = v;
  });
  return {
    portfolio: {
      id: portfolio?.id,
      name: portfolio?.name ?? 'Portfolio',
      ownerEmail: portfolio?.ownerEmail ?? '',
    },
    summary: {
      totalValue: summary?.totalValueTry ?? null,
      totalCost: summary?.totalEntryValueTry ?? null,
      totalPnl: summary?.totalPnlTry ?? null,
      pnlPct: summary?.pnlPercent ?? null,
      dailyPnl: summary?.dailyPnlTry ?? null,
      dailyPnlPct: summary?.dailyPnlPercent ?? null,
    },
    positions: (positions ?? []).map((p) => ({
      code: p.assetCode,
      name: p.assetName ?? '',
      type: p.assetType,
      qty: p.quantity ?? null,
      entryPrice: p.entryPrice ?? null,
      currentPrice: p.currentPrice ?? null,
      marketValue: p.marketValueTry ?? null,
      pnl: p.pnlTry ?? null,
      pnlPct: p.pnlPercent ?? null,
    })),
    allocation: (allocation ?? []).map((a) => ({
      label: a.label ?? a.assetType ?? 'Other',
      percent: a.percent ?? 0,
      color: a.color ?? '',
    })),
    chartImages: charts,
    currency: currency || 'TRY',
    theme: theme === 'DARK' ? 'DARK' : 'LIGHT',
    locale: locale === 'en' ? 'en' : 'tr',
  };
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export default function usePortfolioPdfDownload(args) {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState(null);

  const download = useCallback(async () => {
    setIsPending(true);
    setError(null);
    try {
      const payload = buildPayload(args);
      const response = await api.post('/api/v1/reports/portfolio-pdf', payload, {
        responseType: 'blob',
      });
      const safeName = (args?.portfolio?.name || 'report').toLowerCase().replace(/\s+/g, '-');
      triggerDownload(response.data, `portfolio-${safeName}.pdf`);
    } catch (e) {
      setError(e);
    } finally {
      setIsPending(false);
    }
  }, [args]);

  return { download, isPending, error };
}
