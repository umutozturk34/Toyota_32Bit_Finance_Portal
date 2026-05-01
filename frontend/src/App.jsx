import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './features/auth/AuthContext';
import ToastContainer from './shared/components/Toast';
import ProtectedRoute from './shared/components/ProtectedRoute';
import ErrorBoundary from './shared/components/ErrorBoundary';
import NotFound from './shared/components/NotFound';
import MainLayout from './shared/layouts/MainLayout';
import HomePage from './features/home/HomePage';
import Login from './features/auth/Login';
import Register from './features/auth/Register';
import TwoFactorSetup from './features/auth/TwoFactorSetup';
import News from './features/news';
import NewsDetail from './features/news/NewsDetail';
import MarketDataPage from './features/market/MarketDataPage';
import StocksPage from './features/stock/StocksPage';
import StockDetail from './features/stock/StockDetail';
import CryptoPage from './features/crypto/CryptoPage';
import CryptoDetail from './features/crypto/CryptoDetail';
import ForexPage from './features/forex/ForexPage';
import ForexDetail from './features/forex/ForexDetail';
import FundsPage from './features/fund/FundsPage';
import FundDetail from './features/fund/FundDetail';
import CommoditiesPage from './features/commodity/CommoditiesPage';
import CommodityDetail from './features/commodity/CommodityDetail';
import BondsPage from './features/bond/BondsPage';
import Portfolio from './features/portfolio';
import AdminTrackedAssetsPage from './features/admin/AdminTrackedAssetsPage';

function LandingRedirect() {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) return <Navigate to="/market" replace />;
  return <HomePage />;
}

function PublicOnly({ children }) {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) return <Navigate to="/market" replace />;
  return children;
}

function App() {
  return (
    <AuthProvider>
      <ToastContainer />
      <BrowserRouter>
        <ErrorBoundary>
        <Routes>
          <Route index element={<LandingRedirect />} />
          <Route path="login" element={<PublicOnly><Login /></PublicOnly>} />
          <Route path="register" element={<PublicOnly><Register /></PublicOnly>} />

          <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
            <Route path="2fa" element={<TwoFactorSetup />} />
            <Route path="news" element={<News />} />
            <Route path="news/:id" element={<NewsDetail />} />
            <Route path="market" element={<MarketDataPage />} />
            <Route path="stocks" element={<StocksPage />} />
            <Route path="stocks/:symbol" element={<StockDetail />} />
            <Route path="crypto" element={<CryptoPage />} />
            <Route path="crypto/:id" element={<CryptoDetail />} />
            <Route path="forex" element={<ForexPage />} />
            <Route path="forex/:code" element={<ForexDetail />} />
            <Route path="funds" element={<FundsPage />} />
            <Route path="funds/:code" element={<FundDetail />} />
            <Route path="commodities" element={<CommoditiesPage />} />
            <Route path="commodities/:code" element={<CommodityDetail />} />
            <Route path="bonds" element={<BondsPage />} />
            <Route path="admin/tracked-assets" element={<AdminTrackedAssetsPage />} />
            <Route path="portfolio" element={<Portfolio />} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
        </ErrorBoundary>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
