import axios from 'axios';
import { getToken } from './keycloak';

// Axios instance oluştur
const api = axios.create({
  // Nginx üzerinden backend'e erişim
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1/market',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor - Her istekten önce Keycloak token ekle
api.interceptors.request.use(
  async (config) => {
    try {
      // Keycloak'tan token al (async)
      const token = await getToken();
      
      // Token varsa header'a ekle
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

// Response Interceptor - Hata yönetimi (401 durumunda Keycloak login)
api.interceptors.response.use(
  (response) => {
    // Başarılı yanıtları olduğu gibi döndür
    return response;
  },
  async (error) => {
    // 401 Unauthorized - Token süresi dolmuş veya geçersiz
    if (error.response?.status === 401) {
      console.error('🔐 401 Unauthorized - Keycloak token geçersiz veya süresi dolmuş');
      
      // Sonsuz döngü önlemi: Zaten login sayfasındaysak veya login işlemi yapıyorsak hata fırlat
      if (window.location.pathname.includes('/login')) {
        return Promise.reject(error);
      }
      
      try {
        // Keycloak'a login yönlendir (doLogin fonksiyonu import edelim)
        const { doLogin } = await import('./keycloak');
        console.log('🔄 Redirecting to Keycloak login...');
        doLogin();
      } catch (e) {
        console.error('Failed to initiate Keycloak login:', e);
      }
    }
    
    // Diğer hataları logla
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
