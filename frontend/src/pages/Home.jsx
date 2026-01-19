import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Home.css';

const Home = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  return (
    <div className="home-page">
      <h1>Welcome to Finance Portal</h1>
      <p>Your comprehensive financial management solution.</p>
      
      {!isAuthenticated && (
        <div className="auth-actions">
          <button onClick={() => navigate('/login')} className="btn-primary">
            🔐 Login
          </button>
          <button onClick={() => navigate('/register')} className="btn-secondary">
            ✍️ Register
          </button>
        </div>
      )}

      <div className="features">
        <div className="feature-card">
          <h3>🔐 Secure Authentication</h3>
          <p>Enterprise-grade security with Keycloak integration</p>
        </div>
        <div className="feature-card">
          <h3>📊 Market Data</h3>
          <p>Real-time financial news and market information</p>
        </div>
        <div className="feature-card">
          <h3>💼 Portfolio Management</h3>
          <p>Track and manage your financial portfolio</p>
        </div>
      </div>
    </div>
  );
};

export default Home;
