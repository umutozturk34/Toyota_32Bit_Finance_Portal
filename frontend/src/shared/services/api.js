import axios from 'axios';
import i18n from '../i18n/config';
import { getToken } from '../../features/auth/lib/keycloak';
import { toast } from '../components/feedback/toastBus';
import { TIMINGS } from '../config/uiConfig';
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});
let _lastRateLimitAlert = 0;

api.interceptors.request.use(
  async (config) => {
    try {
      const token = await getToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch { /* token unavailable, continue without auth header */ }
    config.headers['Accept-Language'] = i18n.language || i18n.options.fallbackLng;
    return config;
  },
  (error) => Promise.reject(error)
);
function isAbortError(error) {
  if (!error) return false;
  if (axios.isCancel(error)) return true;
  if (error.name === 'CanceledError' || error.name === 'AbortError') return true;
  if (error.code === 'ERR_CANCELED') return true;
  if (typeof error.message === 'string' && /load failed|aborted|canceled|cancelled/i.test(error.message)) return true;
  return false;
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (isAbortError(error)) {
      return Promise.reject(error);
    }
    if (error.response?.status === 429) {
      const now = Date.now();
      if (now - _lastRateLimitAlert > TIMINGS.RATE_LIMIT_THROTTLE_MS) {
        _lastRateLimitAlert = now;
        const message = error.response?.data?.message
          || i18n.t('toast.rateLimitMessage');
        const retryAfter = error.response?.headers?.['x-rate-limit-retry-after-seconds']
          || error.response?.headers?.['retry-after'];
        toast.rateLimit(message, retryAfter);
      }
      return Promise.reject(error);
    }
    if (error.response?.status === 401) {
      if (!window.location.pathname.includes('/login')) {
        try {
          const { doLogin } = await import('../../features/auth/lib/keycloak');
          doLogin();
        } catch { /* login redirect unavailable */ }
      }
      return Promise.reject(error);
    }
    return Promise.reject(error);
  }
);
export default api;
