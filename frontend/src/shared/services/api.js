import axios from 'axios';
import { getToken } from '../../features/auth/lib/keycloak';
import { toast } from '../components/feedback/Toast';
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
    } catch {}
    return config;
  },
  (error) => Promise.reject(error)
);
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 429) {
      const now = Date.now();
      if (now - _lastRateLimitAlert > TIMINGS.RATE_LIMIT_THROTTLE_MS) {
        _lastRateLimitAlert = now;
        const message = error.response?.data?.message
          || 'Çok fazla istek gönderdin. Lütfen biraz bekle.';
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
        } catch {}
      }
    }
    return Promise.reject(error);
  }
);
export default api;
