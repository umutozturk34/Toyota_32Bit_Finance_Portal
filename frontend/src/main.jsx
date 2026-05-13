import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import './shared/i18n/config'
import App from './App.jsx'
import { STALE } from './shared/constants/query';

if (typeof window !== 'undefined' && 'scrollRestoration' in window.history) {
  window.history.scrollRestoration = 'manual';
}

if (typeof window !== 'undefined') {
  const isAbortLike = (reason) => {
    if (!reason) return false;
    if (reason.name === 'CanceledError' || reason.name === 'AbortError') return true;
    if (reason.code === 'ERR_CANCELED') return true;
    if (typeof reason.message === 'string' && /load failed|aborted|canceled|cancelled/i.test(reason.message)) return true;
    return false;
  };
  window.addEventListener('unhandledrejection', (event) => {
    if (isAbortLike(event.reason)) {
      event.preventDefault();
    }
  });
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: STALE.SHORT,
      retry: (failureCount, error) => {
        const status = error?.response?.status;
        if (status >= 400 && status < 500) return false;
        return failureCount < 1;
      },
      refetchOnWindowFocus: false,
    },
  },
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
