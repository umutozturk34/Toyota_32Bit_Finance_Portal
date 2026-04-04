import axios from 'axios';
import { getToken } from '../../features/auth/keycloak';
import { toast } from '../components/Toast';
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
    } catch (error) {
      console.error('Failed to get Keycloak token:', error);
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);
api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    if (error.response?.status === 429) {
      const now = Date.now();
      if (now - _lastRateLimitAlert > 30000) {
        _lastRateLimitAlert = now;
        const message = error.response?.data?.message
          || 'Çok fazla istek gönderdin. Lütfen biraz bekle.';
        const retryAfter = error.response?.headers?.['x-rate-limit-retry-after-seconds'];
        toast.rateLimit(message, retryAfter);
      }
      return Promise.reject(error);
    }
    if (error.response?.status === 401) {
      console.error('401 Unauthorized - Keycloak token geçersiz veya süresi dolmuş');
      if (window.location.pathname.includes('/login')) {
        return Promise.reject(error);
      }
      try {
        const { doLogin } = await import('../../features/auth/keycloak');
        console.log('Redirecting to Keycloak login...');
        doLogin();
      } catch (e) {
        console.error('Failed to initiate Keycloak login:', e);
      }
    }
    console.error('API Error:', {
      url: error.config?.url,
      method: error.config?.method,
      status: error.response?.status,
      data: error.response?.data,
    });
    return Promise.reject(error);
  }
);
export default api;
