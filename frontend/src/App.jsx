import {
  createBrowserRouter,
  RouterProvider,
  createRoutesFromElements,
  Route,
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom';
import { AuthProvider } from './features/auth/AuthContext';
import { useAuth } from './features/auth/useAuth';
import { ThemeProvider } from './shared/context/ThemeContext';
import LanguageSyncBridge from './shared/i18n/useLanguageSync';
import useOnboardingSync from './shared/hooks/useOnboardingSync';
import usePrefetchOnAuth from './shared/hooks/usePrefetchOnAuth';

function OnboardingBridge() {
  useOnboardingSync();
  return null;
}

function PrefetchBridge() {
  usePrefetchOnAuth();
  return null;
}
import ToastContainer from './shared/components/feedback/Toast';
import ProtectedRoute from './shared/components/auth/ProtectedRoute';
import ErrorBoundary from './shared/components/feedback/ErrorBoundary';
import NotFound from './shared/components/feedback/NotFound';
import MainLayout from './shared/layouts/MainLayout';
import HomePage from './features/home/HomePage';
import News from './features/news';
import NewsDetail from './features/news/components/NewsDetail';
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
import BondDetail from './features/bond/BondDetail';
import ViopPage from './features/viop/ViopPage';
import ViopDetail from './features/viop/ViopDetail';
import Portfolio from './features/portfolio';
import WatchPage from './features/watch/WatchPage';
import AnalyticsPage from './features/analytics/AnalyticsPage';
import LearnPage from './features/learn/LearnPage';
import AdminTrackedAssetsPage from './features/admin/components/AdminTrackedAssetsPage';
import AdminUsersPage from './features/admin/components/AdminUsersPage';

function LandingRedirect() {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) return <Navigate to="/market" replace />;
  return <HomePage />;
}

function RootLayout() {
  // Key the boundary by pathname so a one-off render crash clears on the next navigation; without this
  // the boundary stays latched in its error state forever (it never resets itself across route changes).
  const location = useLocation();
  return (
    <ErrorBoundary key={location.pathname}>
      <Outlet />
    </ErrorBoundary>
  );
}

const router = createBrowserRouter(
  createRoutesFromElements(
    <Route element={<RootLayout />}>
      <Route index element={<LandingRedirect />} />
      <Route path="login" element={<Navigate to="/" replace />} />
      <Route path="register" element={<Navigate to="/" replace />} />
      <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
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
        <Route path="bonds/:seriesCode" element={<BondDetail />} />
        <Route path="viop" element={<ViopPage />} />
        <Route path="viop/:symbol" element={<ViopDetail />} />
        <Route path="admin/tracked-assets" element={<AdminTrackedAssetsPage />} />
        <Route path="admin/users" element={<AdminUsersPage />} />
        <Route path="portfolio" element={<Portfolio />} />
        <Route path="watch" element={<WatchPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="learn" element={<LearnPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Route>
  ),
);

function App() {
  return (
    <AuthProvider>
      <ThemeProvider>
        <LanguageSyncBridge />
        <OnboardingBridge />
        <PrefetchBridge />
        <ToastContainer />
        <RouterProvider router={router} />
      </ThemeProvider>
    </AuthProvider>
  );
}

export default App;
