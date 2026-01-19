import { Outlet, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './MainLayout.css';

const MainLayout = () => {
  const { isAuthenticated, user, login, logout, hasRole } = useAuth();

  return (
    <div className="main-layout">
      <header className="header">
        <div className="container">
          <div className="header-content">
            <Link to="/" className="logo">
              <h1>🏦 Finance Portal</h1>
            </Link>
            
            <nav className="nav">
              <Link to="/">Home</Link>
              <Link to="/users">Users</Link>
              <Link to="/2fa">🔐 2FA</Link>
              <Link to="/about">About</Link>
            </nav>

            <div className="auth-section">
              {isAuthenticated ? (
                <div className="user-info">
                  <div className="user-details">
                    <span className="user-icon">👤</span>
                    <div className="user-text">
                      <span className="username">{user?.username}</span>
                      <span className="user-roles">
                        {hasRole('ADMIN') && <span className="role-badge admin">ADMIN</span>}
                        {hasRole('USER') && <span className="role-badge user">USER</span>}
                      </span>
                    </div>
                  </div>
                  <button className="logout-button" onClick={logout}>
                    🚪 Logout
                  </button>
                </div>
              ) : (
                <button className="login-button" onClick={login}>
                  🔐 Login
                </button>
              )}
            </div>
          </div>
        </div>
      </header>
      
      <main className="main-content">
        <div className="container">
          <Outlet />
        </div>
      </main>
      
      <footer className="footer">
        <div className="container">
          <p>&copy; 2026 Toyota Finance Portal. All rights reserved.</p>
          <p className="powered-by">Powered by Keycloak & Spring Security</p>
        </div>
      </footer>
    </div>
  );
};

export default MainLayout;
