import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './layouts/MainLayout';
import Home from './pages/Home';
import Users from './pages/Users';
import About from './pages/About';
import Login from './pages/Login';
import Register from './pages/Register';
import TwoFactorSetup from './pages/TwoFactorSetup';
import News from './pages/News';
import MarketData from './pages/MarketData';
import Stocks from './pages/Stocks';
import Crypto from './pages/Crypto';
import Forex from './pages/Forex';
import Metals from './pages/Metals';
import ChartView from './pages/ChartView';
function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<MainLayout />}>
            <Route index element={<Home />} />
            <Route path="login" element={<Login />} />
            <Route path="register" element={<Register />} />
            <Route 
              path="users" 
              element={
                <ProtectedRoute>
                  <Users />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="2fa" 
              element={
                <ProtectedRoute>
                  <TwoFactorSetup />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="news" 
              element={
                <ProtectedRoute>
                  <News />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="market" 
              element={
                <ProtectedRoute>
                  <MarketData />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="stocks" 
              element={
                <ProtectedRoute>
                  <Stocks />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="crypto" 
              element={
                <ProtectedRoute>
                  <Crypto />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="forex" 
              element={
                <ProtectedRoute>
                  <Forex />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="metals" 
              element={
                <ProtectedRoute>
                  <Metals />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="charts" 
              element={
                <ProtectedRoute>
                  <ChartView />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="chart/:coinId" 
              element={
                <ProtectedRoute>
                  <ChartView />
                </ProtectedRoute>
              } 
            />
            <Route path="about" element={<About />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
export default App;
