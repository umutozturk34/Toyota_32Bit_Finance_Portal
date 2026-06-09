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
  paramsSerializer: {
    indexes: null,
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
    } catch { void 0; }
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
      const path = window.location.pathname;
      if (!path.includes('/login')) {
        try {
          const { doLogin } = await import('../../features/auth/lib/keycloak');
          doLogin();
        } catch { void 0; }
      }
      return Promise.reject(error);
    }
    const status = error.response?.status;
    const backendMessage = error.response?.data?.message;
    const isValidation = status === 400 && error.response?.data?.errorCode === 'VALIDATION_ERROR';
    let messageKey = null;
    if (!error.response) {
      messageKey = 'error.networkError';
    } else if (status === 502 || status === 503) {
      messageKey = 'error.serverUnavailable';
    } else if (status === 504) {
      messageKey = 'error.gatewayTimeout';
    } else if (status >= 500) {
      messageKey = 'error.serverError';
    } else if (isValidation) {
      messageKey = 'error.validationFailed';
    }
    if (messageKey) {
      const fallback = i18n.t(messageKey);
      toast.error(i18n.t('error.actionFailed'), isValidation ? fallback : (backendMessage || fallback));
    }
    return Promise.reject(error);
  }
);
export default api;
