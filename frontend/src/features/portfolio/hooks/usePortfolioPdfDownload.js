import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../../shared/services/api';

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
  const [elapsedMs, setElapsedMs] = useState(0);
  const argsRef = useRef(args);
  argsRef.current = args;
  const startRef = useRef(0);
  const tickerRef = useRef(null);

  useEffect(() => () => {
    if (tickerRef.current) clearInterval(tickerRef.current);
  }, []);

  const download = useCallback(async () => {
    setIsPending(true);
    setError(null);
    setElapsedMs(0);
    startRef.current = performance.now();
    if (tickerRef.current) clearInterval(tickerRef.current);
    tickerRef.current = setInterval(() => {
      setElapsedMs(performance.now() - startRef.current);
    }, 100);
    try {
      const { portfolio, theme, locale, currency } = argsRef.current;
      const portfolioId = portfolio?.id;
      const resolvedTheme = theme === 'DARK' ? 'DARK' : 'LIGHT';
      const resolvedLocale = locale === 'en' ? 'en' : 'tr';
      const resolvedCurrency = currency || 'TRY';
      const response = await api.post(
        '/reports/portfolio-pdf',
        {
          portfolioId,
          theme: resolvedTheme,
          locale: resolvedLocale,
          currency: resolvedCurrency,
        },
        { responseType: 'blob' }
      );
      const safeName = (portfolio?.name || 'report').toLowerCase().replace(/\s+/g, '-');
      triggerDownload(response.data, `portfolio-${safeName}.pdf`);
    } catch (e) {
      setError(e);
    } finally {
      if (tickerRef.current) {
        clearInterval(tickerRef.current);
        tickerRef.current = null;
      }
      setIsPending(false);
    }
  }, []);

  return { download, isPending, error, elapsedMs };
}
